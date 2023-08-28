(ns ^:no-doc goose.brokers.redis.batch
  (:require
    [goose.batch :as batch]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.retry]
    [goose.utils :as u]
    [taoensso.carmine :as car]
    [taoensso.carmine.locks :as car-locks]))

(def batch-state-keys [:id
                       :callback-fn-sym
                       :created-at
                       :linger-sec
                       :ready-queue
                       :queue
                       :retry-opts])
(defn- set-batch-state
  [{:keys [id jobs] :as batch}]
  (let [batch-state-key (d/prefix-batch id)
        batch-state (select-keys batch batch-state-keys)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        job-ids (map :id jobs)]
    (car/hmset* batch-state-key batch-state)
    (apply car/sadd enqueued-job-set job-ids)))

(defn- enqueue-jobs
  [jobs]
  (doseq [job jobs]
    (car/lpush (:ready-queue job) job)))

(defn enqueue
  [redis-conn batch]
  (redis-cmds/with-transaction redis-conn
    (car/multi)
    (set-batch-state batch)
    (enqueue-jobs (:jobs batch))))

(defn get-batch-state
  [redis-conn id]
  (let [batch-state-key (d/prefix-batch id)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        retrying-job-set (d/construct-batch-job-set id d/retrying-job-set)
        successful-job-set (d/construct-batch-job-set id d/successful-job-set)
        dead-job-set (d/construct-batch-job-set id d/dead-job-set)
        [_ [batch-state enqueued retrying successful dead]]
        (car/atomic redis-conn
          redis-cmds/atomic-lock-attempts
          (car/multi)
          (car/hgetall batch-state-key)
          (car/scard enqueued-job-set)
          (car/scard retrying-job-set)
          (car/scard successful-job-set)
          (car/scard dead-job-set))]
    (when (not-empty batch-state)
      {:batch-state (u/flat-sequence->map batch-state)
       :enqueued    enqueued
       :retrying    retrying
       :successful  successful
       :dead        dead})))

(defn set-batch-expiration
  [conn id linger-sec-str]
  (let [linger-sec (if linger-sec-str
                     (Long/parseLong linger-sec-str)
                     d/redis-batch-linger-sec)
        batch-state-key (d/prefix-batch id)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        retrying-job-set (d/construct-batch-job-set id d/retrying-job-set)
        successful-job-set (d/construct-batch-job-set id d/successful-job-set)
        dead-job-set (d/construct-batch-job-set id d/dead-job-set)]
    (redis-cmds/expire conn batch-state-key linger-sec)
    (redis-cmds/expire conn enqueued-job-set linger-sec)
    (redis-cmds/expire conn retrying-job-set linger-sec)
    (redis-cmds/expire conn successful-job-set linger-sec)
    (redis-cmds/expire conn dead-job-set linger-sec)))

(defn post-batch-exec
  [conn id]
  (car-locks/with-lock conn (concat "post-batch-exec:" id)
                       d/redis-batch-lock-timeout-ms
                       d/redis-batch-lock-wait-ms
                       (let [{:keys [batch-state] :as batch} (get-batch-state conn id)
                             status (batch/status-from-counts batch)
                             {:keys [callback-job-id
                                     callback-fn-sym
                                     linger-sec
                                     queue
                                     ready-queue
                                     retry-opts]} batch-state]
                         (when (= status batch/status-complete)
                           (when (and
                                   (some? callback-fn-sym)
                                   (not callback-job-id))
                             (let [callback-args (-> (select-keys batch [:successful :dead])
                                                     (list)
                                                     (conj id))
                                   callback-job (-> (job/new callback-fn-sym
                                                             callback-args
                                                             queue
                                                             ready-queue
                                                             retry-opts)
                                                    (assoc :batch-id id))]
                               (redis-cmds/enqueue-back conn ready-queue callback-job)
                               (redis-cmds/hset conn (d/prefix-batch id) (name :callback-job-id) (:id callback-job))))
                           (set-batch-expiration conn id linger-sec)))))

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

(defn wrap-state-update [next]
  (fn [{:keys [redis-conn] :as opts}
       {job-id :id batch-id :batch-id :as job}]
    (if batch-id
      (let [src-set (job-source-set job batch-id)]
        (try
          (let [response (next opts job)
                dst-set (job-destination-set job batch-id)]
            (redis-cmds/move-between-sets redis-conn src-set dst-set job-id)
            response)
          (catch Exception ex
            (let [failed-job (goose.retry/set-failed-config job ex)
                  dst-set (job-destination-set failed-job batch-id ex)]
              (redis-cmds/move-between-sets redis-conn src-set dst-set job-id)
              (throw ex)))
          (finally
            ;; Regardless of job success/failure, post-batch-exec must be called.
            ;; Parent function receives return value of `try/catch` block,
            ;; `finally` block runs at the end, but does not override return value.
            (post-batch-exec redis-conn batch-id))))
      (next opts job))))
