(ns goose.metrics.keys
  "All keys have been prefixed with goose.
  For sake of reusable dashboard templates,
  prefer modifying tags over keys."
  (:require
    [goose.defaults :as d]))

(defonce jobs-processed "goose.jobs.processed")
(defonce jobs-success "goose.jobs.success")
(defonce jobs-failure "goose.jobs.failure")
(defonce jobs-recovered "goose.jobs.recovered")

(defonce execution-time "goose.job.execution_time")

(defonce execution-latency "goose.execution.latency")
(defonce schedule-latency "goose.scheduled.latency")
(defonce retry-latency "goose.retry.latency")

(defn queue-size [queue] (str "goose.enqueued." (d/affix-queue queue) ".size"))
(defonce total-enqueued-size "goose.total_enqueued.size")
(defonce schedule-queue-size "goose.scheduled_queue.size")
(defonce dead-queue-size "goose.dead_queue.size")
