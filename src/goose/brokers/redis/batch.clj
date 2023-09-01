(ns ^:no-doc goose.brokers.redis.batch
  (:require
    [goose.batch :as batch]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.metrics :as m]
    [goose.retry]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(defn batch-keys [id]
  {:batch-hash-key     (d/prefix-batch id)
   :enqueued-job-set   (str (d/prefix-batch id) "/" set "enqueued")
   :retrying-job-set   (str (d/prefix-batch id) "/" set "successful")
   :successful-job-set (str (d/prefix-batch id) "/" set "retrying")
   :dead-job-set       (str (d/prefix-batch id) "/" set "dead")})

(defn enqueue
  [redis-conn {:keys [id jobs] :as batch}]
  (let [{:keys [batch-hash-key enqueued-job-set]} (batch-keys id)
        batch-state (dissoc batch :jobs)
        job-ids (map :id jobs)]
    (redis-cmds/with-transaction redis-conn
      (car/multi)
      (car/hmset* batch-hash-key batch-state)
      (apply car/sadd enqueued-job-set job-ids)
      (doseq [job jobs]
        (car/lpush (:ready-queue job) job)))))

(defn- restore-data-types
  [{:keys [linger-sec total-jobs status created-at] :as batch-state}]
  (assoc batch-state
    :linger-sec (Integer/valueOf linger-sec)
    :total-jobs (Integer/valueOf total-jobs)
    :status (keyword status)
    :created-at (Long/valueOf created-at)))

(defn get-batch-state
  [redis-conn id]
  (let [{:keys [batch-hash-key enqueued-job-set retrying-job-set successful-job-set dead-job-set]} (batch-keys id)
        [batch-state enqueued retrying successful dead] (redis-cmds/wcar*
                                                          redis-conn
                                                          (car/parse-map (car/hgetall batch-hash-key) :keywordize)
                                                          (car/scard enqueued-job-set)
                                                          (car/scard retrying-job-set)
                                                          (car/scard successful-job-set)
                                                          (car/scard dead-job-set))]
    (when (not-empty batch-state)
      {:batch-state (restore-data-types batch-state)
       :enqueued    enqueued
       :retrying    retrying
       :successful  successful
       :dead        dead})))

(defn set-batch-expiration
  [redis-conn {:keys [id linger-sec]}]
  (let [{:keys [batch-hash-key enqueued-job-set retrying-job-set successful-job-set dead-job-set]} (batch-keys id)]
    (redis-cmds/with-transaction redis-conn
      (car/multi)
      (car/expire batch-hash-key linger-sec)
      (car/expire enqueued-job-set linger-sec)
      (car/expire retrying-job-set linger-sec)
      (car/expire successful-job-set linger-sec)
      (car/expire dead-job-set linger-sec))))

(defn- record-metrics
  [{:keys [metrics-plugin]}
   {:keys [execute-fn-sym queue]}
   {:keys [id created-at status]}]
  (when (m/enabled? metrics-plugin)
    (let [completion-time (- (u/epoch-time-ms) created-at)
          tags {:batch-id id :execute-fn-sym execute-fn-sym :queue queue}]
      (m/increment metrics-plugin (m/batch-status status) 1 tags)
      (m/timing metrics-plugin m/batch-completion-time completion-time tags))))

(defn- enqueue-callback-on-completion
  [redis-conn batch-id]
  ;; If a job is executed after a batch has been deleted,
  ;; `when-let` guards against nil return from `get-batch-state`.
  (when-let [{{:keys [ready-queue total-jobs]} :batch-state
              :keys                            [batch-state successful dead]} (get-batch-state redis-conn batch-id)]
    (let [status (batch/status-from-job-states successful dead total-jobs)]
      (when (batch/terminal-state? status)
        (let [{:keys [batch-hash-key]} (batch-keys batch-id)
              callback-args (list batch-id status)
              callback (batch/new-callback batch-state callback-args)]
          (redis-cmds/with-transaction redis-conn
            (car/multi)
            (car/rpush ready-queue callback)
            (car/hset batch-hash-key :status status)))))))

(defn- job-source-set
  [job {:keys [enqueued-job-set retrying-job-set]}]
  (if (job/retried? job)
    retrying-job-set
    enqueued-job-set))

(defn- job-destination-set
  ([job batch-keys]
   (job-destination-set job batch-keys nil))
  ([job {:keys [retrying-job-set successful-job-set dead-job-set]} ex]
   (if-not ex
     successful-job-set
     (if (goose.retry/max-retries-reached? job)
       dead-job-set
       retrying-job-set))))

(defn- execute-batch
  [next
   {:keys [redis-conn] :as opts}
   {job-id :id batch-id :batch-id :as job}]
  (let [batch-keys (batch-keys batch-id)
        src (job-source-set job batch-keys)]
    (try
      (let [response (next opts job)
            dst (job-destination-set job batch-keys)]
        (redis-cmds/move-between-sets redis-conn src dst job-id)
        response)
      (catch Exception ex
        (let [failed-job (goose.retry/set-failed-config job ex)
              dst (job-destination-set failed-job batch-keys ex)]
          (redis-cmds/move-between-sets redis-conn src dst job-id)
          (throw ex)))
      (finally
        ;; Regardless of job success/failure, enqueue-callback-on-completion must be called.
        ;; Parent function receives return value of `try/catch` block,
        ;; `finally` block runs at the end, but does not override return value.
        (enqueue-callback-on-completion redis-conn batch-id)))))

(defn- wrap-batch-execution [next]
  (fn [opts
       {:keys [batch-id] :as job}]
    (if batch-id
      (execute-batch next opts job)
      (next opts job))))

(defn- wrap-callback-execution [next]
  (fn [{:keys [redis-conn] :as opts}
       {batch-id :callback-for-batch-id :as job}]
    ;; If a callback is executed after a batch has been deleted,
    ;; `if-let` guards against nil return from `get-batch-state`.
    ;; `(and batch-id ...)` exists to avoid fetching batch-state
    ;; for jobs which aren't a batch callback.
    (if-let [{:keys [batch-state]} (and batch-id (get-batch-state redis-conn batch-id))]
      (let [response (next opts job)]
        (set-batch-expiration redis-conn batch-state)
        (record-metrics opts job batch-state)
        response)
      (next opts job))))

(defn wrap-state-update [next]
  (-> next
      (wrap-batch-execution)
      (wrap-callback-execution)))
