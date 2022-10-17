(ns goose.brokers.redis.orphan-checker
  ^:no-doc
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.consumer :as redis-consumer]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.metrics.keys :as metrics-keys]
    [goose.metrics.protocol :as metrics-protocol]
    [goose.utils :as u]))

(defn- increment-job-recovery-metric
  [metrics-plugin
   {:keys [execute-fn-sym queue]}]
  (when (metrics-protocol/enabled? metrics-plugin)
    (let [tags {:function execute-fn-sym :queue queue}]
      (metrics-protocol/increment metrics-plugin metrics-keys/jobs-recovered 1 tags))))

(defn- replay-orphan-jobs
  [{:keys [redis-conn ready-queue metrics-plugin] :as opts}
   orphan-queue]
  ;; Enqueuing in-progress jobs to front of queue isn't possible
  ;; because Carmine doesn't support `LMOVE` function.
  ;; https://github.com/nilenso/goose/issues/14
  (when-let [job (redis-cmds/dequeue-and-preserve redis-conn orphan-queue ready-queue)]
    (increment-job-recovery-metric metrics-plugin job)
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
      (let [process-count (heartbeat/process-count redis-conn process-set)]
        ;; Sleep for (process-count) minutes + jitters.
        ;; On average, Goose checks for orphan jobs every 1 minute.
        (Thread/sleep (u/sec->ms (+ (* 60 process-count)
                                    (rand-int process-count))))))))
