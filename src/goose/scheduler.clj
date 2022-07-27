(ns goose.scheduler
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn run-at
  [redis-conn epoch-ms
   {:keys [prefixed-queue] :as job}]
  (let [scheduled-job (assoc job :schedule epoch-ms)]
    (if (< epoch-ms (u/epoch-time-ms))
      (redis-cmds/enqueue-front redis-conn prefixed-queue scheduled-job)
      (redis-cmds/enqueue-sorted-set redis-conn d/prefixed-schedule-queue epoch-ms scheduled-job))))

(defn execution-queue
  [job]
  (if (get-in job [:state :error])
    (or (get-in job [:retry-opts :prefixed-retry-queue]) (:prefixed-queue job))
    (:prefixed-queue job)))

(defn run
  [{:keys [internal-thread-pool redis-conn
           scheduler-polling-interval-sec process-set]}]
  (log/info "Polling scheduled jobs...")
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (if-let [jobs (redis-cmds/scheduled-jobs-due-now redis-conn d/prefixed-schedule-queue)]
        (redis-cmds/enqueue-due-jobs-to-front
          redis-conn d/prefixed-schedule-queue
          jobs execution-queue)
        (let [process-count (heartbeat/process-count redis-conn process-set)]
          ; Sleep for process-count * polling-interval + jitters
          ; On average, Goose checks for scheduled jobs
          ; every polling interval configured.
          (Thread/sleep (* 1000 (+ (* scheduler-polling-interval-sec process-count)
                                   (rand-int 3))))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
