(ns goose.brokers.redis.batch
  (:require [goose.brokers.redis.commands :as redis-cmds]
            [goose.defaults :as d]
            [goose.job :as job]
            [goose.retry]
            [taoensso.carmine :as car]))

(defn- set-batch-state
  [{:keys [id jobs] :as batch}]
  (let [batch-state-key (d/prefix-batch id)
        batch-state (select-keys batch [:id :callback-fn-sym])
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

(defn batch-complete?
  [enqueued-set-size retrying-set-size]
  (== (+ enqueued-set-size retrying-set-size) 0))

(defn enqueue-callback
  [_batch-id]
  ()) ; Enqueue the batch callback as a job

(defn move-and-check-batch-completed?
  "Move job-id between 2 sets and check if batch is complete"
  [redis-conn src dst batch-id id]
  (let [[_ [_moved?
            enqueued-set-size
            retrying-set-size]] (redis-cmds/atomic
                                  redis-conn (fn []
                                               (car/multi)
                                               (car/smove src dst id)
                                               (car/scard (d/construct-batch-job-set
                                                            batch-id d/enqueued-job-set))
                                               (car/scard (d/construct-batch-job-set
                                                            batch-id d/retrying-job-set))))
        completed? (batch-complete? enqueued-set-size retrying-set-size)]
    completed?))

(defn update-state [next]
  (fn [{:keys [redis-conn] :as opts}
       {:keys [id batch-id] :as job}]
    (if batch-id
      (let [src (if (job/retried? job)
                  (d/construct-batch-job-set batch-id d/retrying-job-set)
                  (d/construct-batch-job-set batch-id d/enqueued-job-set))]
        (try
          (let [response (next opts job)
                dst (d/construct-batch-job-set batch-id d/successful-job-set)]
            (when (move-and-check-batch-completed? redis-conn src dst batch-id id)
              (enqueue-callback batch-id))
            response)
          (catch Exception ex
            (let [failed-job (goose.retry/set-failed-config job ex)
                  dst (if (goose.retry/max-retries-reached? failed-job)
                        (d/construct-batch-job-set batch-id d/dead-job-set)
                        (d/construct-batch-job-set batch-id d/retrying-job-set))]
              (when (move-and-check-batch-completed? redis-conn src dst batch-id id)
                (enqueue-callback batch-id))
              (throw ex)))))
      (next opts job))))
