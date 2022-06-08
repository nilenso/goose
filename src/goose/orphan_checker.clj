(ns goose.orphan-checker
  (:require
    [goose.executor :as executor]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]))

(def ^:private initial-cursor "0")

(defn- reenqueue-orphan-jobs
  [redis-conn prefixed-queue process]
  (let [orphan-queue (executor/preservation-queue process)]
    (loop []
      ; Enqueuing in-progress jobs to front of queue isn't possible
      ; because Carmine doesn't support `LMOVE` function.
      ; https://github.com/nilenso/goose/issues/14
      (when (r/dequeue-and-preserve redis-conn orphan-queue prefixed-queue)
        (recur)))))

(defn- check-liveness
  [redis-conn prefixed-queue
   process-set processes]
  (doseq [process processes]
    (when-not (heartbeat/alive? redis-conn process)
      (reenqueue-orphan-jobs redis-conn prefixed-queue process)
      (r/del-from-set redis-conn process-set process))))

(defn run
  [{:keys [id internal-thread-pool
           redis-conn prefixed-queue process-set]}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (loop [cursor nil]
        (let [current (or cursor initial-cursor)
              [next processes] (r/scan-set redis-conn process-set current 1)]
          (check-liveness
            redis-conn prefixed-queue process-set
            (remove #{id} processes))
          (when-not (= next initial-cursor)
            (recur next))))
      (let [process-count (heartbeat/process-count redis-conn process-set)]
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose checks for orphan jobs every 1 minute.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))

