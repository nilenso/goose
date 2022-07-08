(ns goose.statsd.statsd
  (:require
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]

    [clj-statsd :as statsd]))

(defonce default-opts
         {:host        "127.0.0.1"
          :port        8125
          :sample-rate 1.0
          :tags        #{}})

(defonce jobs-count "jobs.count")
(defonce jobs-success "jobs.success")
(defonce jobs-failure "jobs.failure")

(defonce execution-time "job.execution_time")

(defonce retry-latency "retry.latency")
(defonce schedule-latency "scheduled.latency")
(defonce execution-latency "execution.latency")

(defonce queue-size "queue.size")
(defonce schedule-queue-size "scheduled-queue.size")
(defonce dead-queue-size "dead-queue.size")

(defn initialize
  [{:keys [host port]}]
  (when (and host port)
    (statsd/setup host port :prefix d/statsd-prefix)))

(defn add-queue-tag
  [{:keys [tags] :as opts} queue]
  (assoc opts :tags (merge tags (str "queue:" queue))))

(defn add-function-tag
  [{:keys [tags] :as opts} function]
  (assoc opts :tags (merge tags (str "function:" function))))

(defmacro timed-execution
  "Time the execution of the provided code."
  [rate tags & body]
  `(let [start# (u/epoch-time-ms)]
     (try
       ~@body
       (finally
         (statsd/timing execution-time (- (u/epoch-time-ms) start#) ~rate ~tags)))))

(defmacro emit-metrics
  [sample-rate tags run-fn]
  `(try
     (statsd/increment jobs-count 1 ~sample-rate ~tags)
     (timed-execution ~sample-rate ~tags ~run-fn)
     (statsd/increment jobs-success 1 ~sample-rate ~tags)
     (catch Exception ex#
       (statsd/increment jobs-failure 1 ~sample-rate ~tags)
       (throw ex#))))

(defn timing
  [{:keys [sample-rate tags]} key value]
  ; When a job is executed using API, latency might be negative.
  ; Ignore negative values.
  (when (pos? value)
    (statsd/timing key value sample-rate tags)))

(defn run
  [{{:keys [sample-rate tags]} :statsd-opts
    :keys                      [internal-thread-pool redis-conn
                                prefixed-queue process-set]}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (let [size-map
            {queue-size          (r/list-size redis-conn prefixed-queue)
             schedule-queue-size (r/set-size redis-conn d/prefixed-schedule-queue)
             dead-queue-size     (r/set-size redis-conn d/prefixed-dead-queue)}]
        ; Using doseq because map is lazy and needs to be wrapped inside doall.
        (doseq [[k v] size-map]
          (statsd/gauge k v sample-rate tags)))
      (let [process-count (heartbeat/process-count redis-conn process-set)]
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose sends queue level stats every 1 minute.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))
