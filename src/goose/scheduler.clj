(ns goose.scheduler
  (:require
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(def schedule-queue (u/prefix-queue d/schedule-queue))

(defn run-at
  [redis-conn epoch-ms
   {:keys [queue] :as job}]
  (let [scheduled-job (assoc job :schedule epoch-ms)]
    (if (< epoch-ms (u/epoch-time-ms))
      (r/enqueue-front redis-conn queue scheduled-job)
      (r/enqueue-sorted-set redis-conn schedule-queue epoch-ms scheduled-job))))

(defn- execution-queue
  [job]
  (if (get-in job [:state :error])
    (or (get-in job [:retry-opts :retry-queue]) (:queue job))
    (:queue job)))

(defn run
  [{:keys [internal-thread-pool redis-conn queue
           scheduler-polling-interval-sec]}]
  (u/while-pool
    internal-thread-pool
    (log/info "Polling scheduled jobs...")
    (u/log-on-exceptions
      (if-let [jobs (r/scheduled-jobs-due-now redis-conn schedule-queue)]
        (r/enqueue-due-jobs-to-front
          redis-conn schedule-queue
          jobs execution-queue)
        (let [process-count (heartbeat/process-count redis-conn queue)]
          ; Sleep for process-count * polling-interval + jitters
          ; On average, Goose checks for scheduled jobs
          ; every polling interval configured.
          (Thread/sleep (* 1000 (+ (* scheduler-polling-interval-sec process-count)
                                   (rand-int 3))))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
