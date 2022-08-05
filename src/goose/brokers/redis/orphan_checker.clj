(ns goose.brokers.redis.orphan-checker
  {:no-doc true}
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.executor :as  redis-executor]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.statsd :as statsd]
    [goose.utils :as u]))

(defn- reenqueue-orphan-jobs
  [{:keys [redis-conn prefixed-queue statsd-opts] :as opts}
   orphan-queue]
  ; Enqueuing in-progress jobs to front of queue isn't possible
  ; because Carmine doesn't support `LMOVE` function.
  ; https://github.com/nilenso/goose/issues/14
  (when-let [job (redis-cmds/dequeue-and-preserve redis-conn orphan-queue prefixed-queue)]
    (statsd/increment-recovery statsd-opts job)
    #(reenqueue-orphan-jobs opts orphan-queue)))

(defn- check-liveness
  [{:keys [redis-conn process-set] :as opts} processes]
  (doseq [process processes]
    (when-not (heartbeat/alive? redis-conn process)
      (trampoline
        reenqueue-orphan-jobs
        opts (redis-executor/preservation-queue process))
      (redis-cmds/del-from-set redis-conn process-set process))))

(defn run
  [{:keys [id internal-thread-pool redis-conn process-set]
    :as   opts}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (let [processes (redis-cmds/find-in-set redis-conn process-set identity)]
        (check-liveness opts (remove #{id} processes)))
      (let [process-count (heartbeat/process-count redis-conn process-set)]
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose checks for orphan jobs every 1 minute.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))
