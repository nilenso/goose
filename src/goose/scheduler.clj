(ns goose.scheduler
  (:require
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(def default-opts
  "perform-at & perform-in-sec opts are mutually exclusive."
  {:perform-at     nil
   :perform-in-sec nil})

(defn run-at
  [{:keys [perform-at perform-in-sec]}]
  (cond
    perform-at
    (u/epoch-time-ms perform-at)

    perform-in-sec
    (+ (* 1000 perform-in-sec) (u/epoch-time-ms))))

(defn run
  [{:keys [thread-pool redis-conn schedule-queue
           scheduler-polling-interval-sec]}]
  (u/while-pool
    thread-pool
    (log/info "Polling Scheduled Jobs...")
    (u/log-on-exceptions
      (if-let [jobs (r/scheduled-jobs-due-now redis-conn schedule-queue)]
        (r/enqueue-due-jobs-to-front redis-conn schedule-queue jobs)
        (Thread/sleep (* 1000 scheduler-polling-interval-sec)))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
