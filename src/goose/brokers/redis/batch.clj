(ns goose.brokers.redis.batch
  (:require [goose.brokers.redis.commands :as redis-cmds]
            [goose.defaults :as d]
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
