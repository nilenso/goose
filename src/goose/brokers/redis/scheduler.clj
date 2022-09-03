(ns goose.brokers.redis.scheduler
  {:no-doc true}
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn run-at
  [redis-conn epoch-ms
   {:keys [prefixed-queue] :as job}]
  (let [scheduled-job (assoc job :schedule epoch-ms)]
    (if (< epoch-ms (u/epoch-time-ms))
      (redis-cmds/enqueue-front redis-conn prefixed-queue scheduled-job)
      (redis-cmds/enqueue-sorted-set redis-conn d/prefixed-schedule-queue epoch-ms scheduled-job))))

(defn run
  [{:keys [internal-thread-pool redis-conn
           scheduler-polling-interval-sec]}]
  (log/info "Polling scheduled jobs...")
  (u/log-on-exceptions
    (u/while-pool
      internal-thread-pool
      (if-let [jobs (redis-cmds/scheduled-jobs-due-now redis-conn d/prefixed-schedule-queue)]
        (redis-cmds/enqueue-due-jobs-to-front
          redis-conn d/prefixed-schedule-queue
          jobs job/execution-queue)
        ; Instead of sleeping when due jobs are found,
        ; Goose immediately polls to check if more jobs are due.
        (let [total-process-count (heartbeat/total-process-count redis-conn)]
          ; Sleep for total-process-count * polling-interval + jitters
          ; Regardless of number of processes,
          ; On average, Goose checks for scheduled jobs
          ; every polling interval configured to reduce load on Redis.
          ; All worker processes must have same polling interval.
          (Thread/sleep (* 1000 (+ (* scheduler-polling-interval-sec total-process-count)
                                   (rand-int 3))))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
