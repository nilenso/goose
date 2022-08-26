(ns goose.metrics.keys
  (:require
    [goose.defaults :as d]))

(defonce prefix "goose.")

(defonce jobs-processed "jobs.processed")
(defonce jobs-success "jobs.success")
(defonce jobs-failure "jobs.failure")
(defonce jobs-recovered "jobs.recovered")

(defonce execution-time "job.execution_time")

(defonce execution-latency "execution.latency")
(defonce schedule-latency "scheduled.latency")
(defonce retry-latency "retry.latency")

(defn queue-size [queue] (str "enqueued." (d/affix-queue queue) ".size"))
(defonce total-enqueued-size "total_enqueued.size")
(defonce schedule-queue-size "scheduled_queue.size")
(defonce dead-queue-size "dead_queue.size")
