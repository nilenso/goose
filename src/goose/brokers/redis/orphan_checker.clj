(ns ^:no-doc goose.brokers.redis.orphan-checker
  (:require
   [goose.brokers.redis.commands :as redis-cmds]
   [goose.brokers.redis.consumer :as redis-consumer]
   [goose.brokers.redis.heartbeat :as heartbeat]
   [goose.metrics :as m]
   [goose.utils :as u]))

(defn- replay-orphan-jobs
  [{:keys [redis-conn ready-queue metrics-plugin] :as opts}
   orphan-queue]
  ;; Orphan jobs are re-enqueued to front of ready queue for prioritized execution.
  (when-let [job (redis-cmds/dequeue-and-preserve redis-conn orphan-queue ready-queue)]
    (m/increment-job-recovery-metric metrics-plugin job)
    #(replay-orphan-jobs opts orphan-queue)))

(defn- check-liveness
  [{:keys [redis-conn process-set] :as opts}
   processes]
  (doseq [process processes]
    (when-not (heartbeat/alive? redis-conn process)
      (trampoline
       replay-orphan-jobs
       opts (redis-consumer/preservation-queue process))
      (redis-cmds/del-from-set redis-conn process-set process))))

(defn run
  [{:keys [id internal-thread-pool redis-conn process-set] :as opts}]
  (u/log-on-exceptions
   (u/while-pool
    internal-thread-pool
    (let [processes (redis-cmds/find-in-set redis-conn process-set identity)]
      (check-liveness opts (remove #{id} processes)))
    (let [local-workers-count (heartbeat/local-workers-count redis-conn process-set)]
        ;; Scheduler & metrics runners derive sleep time from global-workers-count.
        ;; Orphan checker only recovers jobs from its ready queue;
        ;; hence it takes local-workers-count into account for sleeping.
        ;; Sleep for (local-workers-count) minutes + jitters.
        ;; On average, Goose checks for orphan jobs every 1 minute.
      (u/sleep 60 local-workers-count)))))
