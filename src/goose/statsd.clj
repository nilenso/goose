(ns goose.statsd
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [clj-statsd]))

(defonce prefix "goose.")
(defonce jobs-processed "jobs.processed")
(defonce jobs-success "jobs.success")
(defonce jobs-failure "jobs.failure")
(defonce jobs-recovered "jobs.recovered")

(defonce execution-time "job.execution_time")

(defonce execution-latency "execution.latency")
(defonce schedule-latency "scheduled.latency")
(defonce retry-latency "retry.latency")

(defonce total-enqueued-size "total_enqueued.size")
(defonce schedule-queue-size "scheduled_queue.size")
(defonce dead-queue-size "dead_queue.size")

(def default-opts
  "Default config for StatsD Metrics."
  {:enabled?    true
   :host        "localhost"
   :port        8125
   :sample-rate 1.0
   :tags        {}})

(defn build-tags
  [tags]
  (map
    (fn [[key value]] (str (name key) ":" value))
    tags))

(defn ^:no-doc initialize
  [{:keys [enabled? host port]}]
  (when enabled?
    (clj-statsd/setup host port :prefix prefix)))

(defn ^:no-doc increment-recovery
  [{:keys [enabled? sample-rate tags]}
   {:keys [execute-fn-sym queue]}]
  (when enabled?
    (let [tags-list (build-tags (assoc tags :function execute-fn-sym
                                            :queue queue))]
      (clj-statsd/increment jobs-recovered 1 sample-rate tags-list))))

(defn ^:no-doc wrap-metrics
  [next]
  (fn [{{:keys [enabled? sample-rate tags]} :statsd-opts
        :as                                 opts}
       {[job-type latency] :latency
        :keys              [execute-fn-sym queue]
        :as                job}]
    (if enabled?
      (let [tags-list (build-tags (assoc tags :function execute-fn-sym
                                              :queue queue))
            start (u/epoch-time-ms)]
        (try
          (clj-statsd/increment jobs-processed 1 sample-rate tags-list)
          ; When a job is executed using API, latency might be negative.
          ; Ignore negative values.
          (when (pos? latency)
            (clj-statsd/timing job-type latency sample-rate tags-list))
          (next opts job)
          (clj-statsd/increment jobs-success 1 sample-rate tags-list)
          (catch Exception ex
            (clj-statsd/increment jobs-failure 1 sample-rate tags-list)
            (throw ex))
          (finally
            (clj-statsd/timing execution-time (- (u/epoch-time-ms) start) sample-rate tags-list))))
      (next opts job))))

(defn statsd-queue-metric
  [queue]
  (str "enqueued." (d/affix-queue queue) ".size"))
