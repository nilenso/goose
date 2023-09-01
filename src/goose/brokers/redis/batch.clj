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

(def batch-state-keys [:id
                       :callback-fn-sym
                       :linger-sec
                       :queue
                       :ready-queue
                       :retry-opts
                       :total-jobs
                       :status
                       :created-at])

(defn- set-batch-state
  [{:keys [id jobs] :as batch}]
  (let [batch-state-key (d/prefix-batch id)
        batch-state (select-keys batch batch-state-keys)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        job-ids (map :id jobs)]
    (car/hmset* batch-state-key batch-state)
    (apply car/sadd enqueued-job-set job-ids)))

(defn- enqueue-jobs [jobs]
  (doseq [job jobs]
    (car/lpush (:ready-queue job) job)))

(defn enqueue
  [redis-conn batch]
  (redis-cmds/with-transaction redis-conn
    (car/multi)
    (set-batch-state batch)
    (enqueue-jobs (:jobs batch))))

(defn- restore-data-types
  [{:keys [linger-sec total-jobs status created-at] :as batch-state}]
  (assoc batch-state
    :linger-sec (Integer/valueOf linger-sec)
    :total-jobs (Integer/valueOf total-jobs)
    :status (keyword status)
    :created-at (Long/valueOf created-at)))

(defn get-batch-state
  [redis-conn id]
  (let [batch-state-key (d/prefix-batch id)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        retrying-job-set (d/construct-batch-job-set id d/retrying-job-set)
        successful-job-set (d/construct-batch-job-set id d/successful-job-set)
        dead-job-set (d/construct-batch-job-set id d/dead-job-set)
        [batch-state enqueued retrying successful dead] (redis-cmds/wcar*
                                                          redis-conn
                                                          (car/parse-map (car/hgetall batch-state-key) :keywordize)
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
  (let [batch-state-key (d/prefix-batch id)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        retrying-job-set (d/construct-batch-job-set id d/retrying-job-set)
        successful-job-set (d/construct-batch-job-set id d/successful-job-set)
        dead-job-set (d/construct-batch-job-set id d/dead-job-set)]
    (redis-cmds/with-transaction redis-conn
      (car/multi)
      (car/expire batch-state-key linger-sec)
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
        (let [batch-state-key (d/prefix-batch batch-id)
              callback-args (list batch-id status)
              callback (batch/new-callback batch-state callback-args)]
          (redis-cmds/with-transaction redis-conn
            (car/multi)
            (car/rpush ready-queue callback)
            (car/hset batch-state-key :status status)))))))

(defn- job-source-set
  [job batch-id]
  (if (job/retried? job)
    (d/construct-batch-job-set batch-id d/retrying-job-set)
    (d/construct-batch-job-set batch-id d/enqueued-job-set)))

(defn- job-destination-set
  ([job batch-id]
   (job-destination-set job batch-id nil))
  ([job batch-id ex]
   (if-not ex
     (d/construct-batch-job-set batch-id d/successful-job-set)
     (if (goose.retry/max-retries-reached? job)
       (d/construct-batch-job-set batch-id d/dead-job-set)
       (d/construct-batch-job-set batch-id d/retrying-job-set)))))

(defn- execute-batch
  [next
   {:keys [redis-conn] :as opts}
   {job-id :id batch-id :batch-id :as job}]
  (let [src (job-source-set job batch-id)]
    (try
      (let [response (next opts job)
            dst (job-destination-set job batch-id)]
        (redis-cmds/move-between-sets redis-conn src dst job-id)
        response)
      (catch Exception ex
        (let [failed-job (goose.retry/set-failed-config job ex)
              dst (job-destination-set failed-job batch-id ex)]
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
