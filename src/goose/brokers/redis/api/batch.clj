(ns ^:no-doc goose.brokers.redis.api.batch
  (:require [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.batch :as batch]
            [goose.brokers.redis.commands :as redis-cmds]
            [goose.defaults :as d]))

(defn status [redis-conn id]
  (batch/get-batch-state redis-conn id))

(defn delete [redis-conn id]
  (let [batch-state-key (d/prefix-batch id)
        enqueued-job-set (d/construct-batch-job-set id d/enqueued-job-set)
        retrying-job-set (d/construct-batch-job-set id d/retrying-job-set)
        successful-job-set (d/construct-batch-job-set id d/successful-job-set)
        dead-job-set (d/construct-batch-job-set id d/dead-job-set)
        {:keys [batch-state]} (batch/get-batch-state redis-conn id)
        {{:keys [retry-queue]} :retry-opts :keys [queue]} batch-state]

    ;; delete enqueued jobs
    (let [enqueued-job-ids (redis-cmds/set-members redis-conn enqueued-job-set)]
      (doseq [job-id enqueued-job-ids]
        (when-let [job (enqueued-jobs/find-by-id redis-conn queue job-id)]
          (enqueued-jobs/delete redis-conn job))))

    ;; delete retrying jobs
    (let [retried-job-ids (redis-cmds/set-members redis-conn retrying-job-set)]
      (doseq [job-id retried-job-ids]
        (let [retrying-job-queue (or retry-queue queue)
              job-scheduled-for-retry (scheduled-jobs/find-by-id redis-conn job-id)]
          (if job-scheduled-for-retry
            (scheduled-jobs/delete redis-conn job-scheduled-for-retry)
            (when-let [job-enqueued-for-retry (enqueued-jobs/find-by-id redis-conn retrying-job-queue job-id)]
              (enqueued-jobs/delete redis-conn job-enqueued-for-retry))))))

    ;; delete batch
    (redis-cmds/del-keys redis-conn [batch-state-key enqueued-job-set retrying-job-set successful-job-set dead-job-set])))
