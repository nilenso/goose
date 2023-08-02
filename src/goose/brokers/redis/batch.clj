(ns goose.brokers.redis.batch
  (:require [goose.brokers.redis.commands :as redis-cmds]
            [goose.defaults :as d]
            [goose.job :as job]
            [goose.retry]
            [goose.utils :as u]
            [taoensso.carmine :as car]))

(defn- set-batch-state
  [{:keys [id jobs] :as batch}]
  (let [batch-state-key (d/prefix-batch id)
        batch-state (select-keys batch [:id :callback-fn-sym :created-at])
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
            (redis-cmds/move-between-sets redis-conn src dst id)
            response)
          (catch Exception ex
            (let [failed-job (goose.retry/set-failed-config job ex)
                  dst (if (goose.retry/max-retries-reached? failed-job)
                        (d/construct-batch-job-set batch-id d/dead-job-set)
                        (d/construct-batch-job-set batch-id d/retrying-job-set))]
              (redis-cmds/move-between-sets redis-conn src dst id)
              (throw ex)))))
      (next opts job))))

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
