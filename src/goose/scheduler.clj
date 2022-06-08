(ns goose.scheduler
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(def schedule-queue (u/prefix-queue d/schedule-queue))

(defn schedule-job
  [redis-conn schedule
   {:keys [queue] :as job}]
  (let [scheduled-job (assoc job :schedule schedule)]
    (if (< schedule (u/epoch-time-ms))
      (r/enqueue-front redis-conn queue scheduled-job)
      (r/enqueue-sorted-set redis-conn schedule-queue schedule scheduled-job))))

(defn- execution-queue
  [job]
  (if (get-in job [:dynamic-config :error])
    (or (get-in job [:retry-opts :retry-queue]) (:queue job))
    (:queue job)))

(defn run
  [{:keys [internal-thread-pool redis-conn
           scheduler-polling-interval-sec]}]
  (u/while-pool
    internal-thread-pool
    (log/info "Polling scheduled jobs...")
    (u/log-on-exceptions
      (if-let [jobs (r/scheduled-jobs-due-now redis-conn schedule-queue)]
        (r/enqueue-due-jobs-to-front
          redis-conn schedule-queue
          jobs (group-by execution-queue jobs))
        (Thread/sleep (* 1000 (+ (rand-int 3) scheduler-polling-interval-sec))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
