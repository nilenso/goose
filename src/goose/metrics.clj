(ns goose.metrics
  "Defines protocol for Metrics Backend.
  - [Monitoring & Alerting wiki](https://github.com/nilenso/goose/wiki/Monitoring-&-Alerting)"
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defonce ^:no-doc jobs-processed "jobs.processed")
(defonce ^:no-doc jobs-success "jobs.succeeded")
(defonce ^:no-doc jobs-failure "jobs.failed")
(defonce ^:no-doc jobs-recovered "jobs.recovered")
(defn ^:no-doc batch-status [status]
  (str "batch." (name status)))

(defonce ^:no-doc execution-time "job.execution_time")
(defonce ^:no-doc batch-completion-time "batch.completion_time")

(defonce ^:no-doc execution-latency "execution.latency")
(defonce ^:no-doc schedule-latency "scheduled.latency")
(defonce ^:no-doc cron-schedule-latency "cron_scheduled.latency")
(defonce ^:no-doc retry-latency "retry.latency")

(defn ^:no-doc format-queue-count [queue]
  (format "enqueued_jobs.%s.count" (d/affix-queue queue)))
(defonce ^:no-doc total-enqueued-jobs-count "total_enqueued_jobs.count")
(defonce ^:no-doc schedule-jobs-count "scheduled_jobs.count")
(defonce ^:no-doc periodic-jobs-count "periodic_jobs.count")
(defonce ^:no-doc dead-jobs-count "dead_jobs.count")
(defonce ^:no-doc batches-count "batches.count")

(defprotocol Metrics
  "Protocol that Metrics Backends should implement
   to publish Goose metrics to respective backends.
   - [Guide to Custom Metrics Backend](https://github.com/nilenso/goose/wiki/Guide-to-Custom-Metrics-Backend)"
  (enabled? [this] "Returns true if metrics is enabled.")
  (gauge [this key value tags] "Set gauge of given key")
  (increment [this key value tags] "Increment given key by value.")
  (timing [this key duration tags] "Record duration of given key."))

;;; `nil` behaves equivalent to a disabled metric.
(extend-protocol Metrics
  nil
  (enabled? [_] false)
  (gauge [_ _ _ _] (log/error "Called `gauge` on nil metrics-plugin"))
  (increment [_ _ _ _] (log/error "Called `increment` on nil metrics-plugin"))
  (timing [_ _ _ _] (log/error "Called `timing` on nil metrics-plugin")))

(defn ^:no-doc increment-job-recovery-metric
  [metrics-plugin
   {:keys [execute-fn-sym queue]}]
  (when (enabled? metrics-plugin)
    (let [tags {:function execute-fn-sym :queue queue}]
      (increment metrics-plugin jobs-recovered 1 tags))))

(defn- record-metrics
  [next
   {:keys [metrics-plugin] :as opts}
   {[job-type latency] :latency
    :keys              [execute-fn-sym queue]
    :as                job}]
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
        (timing metrics-plugin execution-time (- (u/epoch-time-ms) start) tags)))))

(defn ^:no-doc wrap-metrics
  [next]
  (fn [{:keys [metrics-plugin] :as opts}
       job]
    (if (enabled? metrics-plugin)
      (record-metrics next opts job)
      (next opts job))))
