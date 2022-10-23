(ns goose.metrics
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]))

(defonce jobs-processed "jobs.processed")
(defonce jobs-success "jobs.succeeded")
(defonce jobs-failure "jobs.failed")
(defonce jobs-recovered "jobs.recovered")

(defonce execution-time "job.execution_time")

(defonce execution-latency "execution.latency")
(defonce schedule-latency "scheduled.latency")
(defonce cron-schedule-latency "cron_scheduled.latency")
(defonce retry-latency "retry.latency")

(defn format-queue-size [queue]
  (format "enqueued.%s.size" (d/affix-queue queue)))
(defonce total-enqueued-size "total_enqueued.size")
(defonce schedule-queue-size "scheduled_queue.size")
(defonce periodic-jobs-size "periodic_jobs.size")
(defonce dead-queue-size "dead_queue.size")

(defprotocol Metrics
  (enabled? [this])
  (gauge [this key value tags])
  (increment [this key value tags])
  (timing [this key duration tags]))

(defn ^:no-doc wrap-metrics
  [next]
  (fn [{:keys [metrics-plugin] :as opts}
       {[job-type latency] :latency
        :keys              [execute-fn-sym queue]
        :as                job}]
    (if (enabled? metrics-plugin)
      (let [tags {:function execute-fn-sym :queue queue}
            start (u/epoch-time-ms)]
        (try
          ;; When a job is executed using API, latency might be negative.
          (when (pos? latency)
            (timing metrics-plugin job-type latency tags))
          (next opts job)
          (increment metrics-plugin jobs-success 1 tags)
          (catch Exception ex
            (increment metrics-plugin jobs-failure 1 tags)
            (throw ex))
          (finally
            (increment metrics-plugin jobs-processed 1 tags)
            (timing metrics-plugin execution-time (- (u/epoch-time-ms) start) tags))))
      (next opts job))))
