(ns goose.orphan-checker
  (:require
    [goose.executor :as executor]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]))

(def ^:private initial-cursor "0")

(defn enqueue-dead-processes-jobs
  [redis-conn prefixed-queue
   process-set processes]
  (doseq [process processes]
    (when-not (heartbeat/alive? redis-conn process)
      (loop []
        ; TODO: Move orphan jobs to front of prefixed-queue.
        (when (r/dequeue-reliable redis-conn (executor/execution-queue process) prefixed-queue)
          (recur)))
      (r/del-from-set redis-conn process-set process))))

(defn run
  [{:keys [id internal-thread-pool
           redis-conn prefixed-queue queue]}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (loop [cursor nil]
        (let [current (or cursor initial-cursor)
              process-set (heartbeat/process-set queue)
              [next processes] (r/scan-set redis-conn process-set current 1)]
          (enqueue-dead-processes-jobs
            redis-conn prefixed-queue process-set
            (remove #{id} processes))
          (when-not (= next initial-cursor)
            (recur next))))
      (let [process-count (heartbeat/process-count redis-conn queue)]
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose checks for orphan jobs every 1 minute.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))

