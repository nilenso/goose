(ns goose.statsd.statsd
  (:require
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]

    [clj-statsd :as statsd]))

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

(defonce default-opts
         {:host        "127.0.0.1"
          :port        8125
          :sample-rate 1.0
          :tags        {}})

(defn- build-tags
  [tags]
  (map
    (fn [[key value]] (str (name key) ":" (name value)))
    tags))

(defn initialize
  [{:keys [host port]}]
  (when (and host port)
    (statsd/setup host port :prefix d/statsd-prefix)))

(defn run-job-and-send-metrics
  [{:keys [sample-rate tags]} [key latency] fn-name run-fn]
  (let [tags-list (build-tags (assoc tags :function fn-name))
        start (u/epoch-time-ms)]
    (try
      (statsd/increment jobs-count 1 sample-rate tags-list)
      ; When a job is executed using API, latency might be negative.
      ; Ignore negative values.
      (when (pos? latency)
        (statsd/timing key latency sample-rate tags-list))
      (run-fn)
      (statsd/increment jobs-success 1 sample-rate tags-list)
      (catch Exception ex
        (statsd/increment jobs-failure 1 sample-rate tags-list)
        (throw ex))
      (finally
        (statsd/timing execution-time (- (u/epoch-time-ms) start) sample-rate tags-list)))))

(defn run
  [{{:keys [sample-rate tags]} :statsd-opts
    :keys                      [internal-thread-pool redis-conn
                                prefixed-queue process-set]}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (let [tags-sans-queue-list (build-tags (dissoc tags :queue))
            tags-list (build-tags tags)
            size-map {queue-size          [(r/list-size redis-conn prefixed-queue) tags-list]
                      schedule-queue-size [(r/sorted-set-size redis-conn d/prefixed-schedule-queue) tags-sans-queue-list]
                      dead-queue-size     [(r/sorted-set-size redis-conn d/prefixed-dead-queue) tags-sans-queue-list]}]
        ; Using doseq instead of map, because map is lazy.
        (doseq [[k [v tags]] size-map]
          (statsd/gauge k v sample-rate tags)))
      (let [process-count (heartbeat/process-count redis-conn process-set)]
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose sends queue level stats every 1 minute.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))
