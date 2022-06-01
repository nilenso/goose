(ns goose.scheduler
  (:require
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]
    [goose.defaults :as d]))

(def default-opts
  "perform-at & perform-in-sec opts are mutually exclusive."
  {:perform-at     nil
   :perform-in-sec nil})

(defn scheduled-time
  [{:keys [perform-at perform-in-sec]}]
  (cond
    perform-at
    (u/epoch-time-ms perform-at)

    perform-in-sec
    (u/add-sec perform-in-sec)))

(defn- internal-opts
  [queue time]
  (if (< time (u/epoch-time-ms))
    {:redis-fn goose.redis/enqueue-front
     :queue    queue}
    {:redis-fn goose.redis/enqueue-sorted-set
     :queue    (u/prefix-queue d/schedule-queue)
     :run-at   time}))

(defn update-job-schedule
  [job time]
  (assoc job
    :internal-opts (internal-opts (:queue job) time)))

(defn run
  [{:keys [thread-pool redis-conn schedule-queue
           scheduler-polling-interval-sec]}]
  (u/while-pool
    thread-pool
    (log/info "Polling scheduled jobs...")
    (u/log-on-exceptions
      (if-let [jobs (r/scheduled-jobs-due-now redis-conn schedule-queue)]
        (r/enqueue-due-jobs-to-front redis-conn schedule-queue jobs)
        (Thread/sleep (* 1000 scheduler-polling-interval-sec)))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
