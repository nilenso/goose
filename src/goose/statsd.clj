(ns goose.statsd
  (:require
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.utils :as u]

    [clj-statsd :as statsd]))

(defonce prefix "goose.")
(defonce jobs-processed "jobs.processed")
(defonce jobs-success "jobs.success")
(defonce jobs-failure "jobs.failure")
(defonce jobs-recovered "jobs.recovered")

(defonce execution-time "job.execution_time")

(defonce retry-latency "retry.latency")
(defonce schedule-latency "scheduled.latency")
(defonce execution-latency "execution.latency")

(defonce enqueued-size "enqueued.size")
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
    (fn [[key value]] (str (name key) ":" value))
    tags))

(defn initialize
  [{:keys [host port]}]
  (when (and host port)
    (statsd/setup host port :prefix prefix)))

(defn increment-recovery
  [{:keys [sample-rate tags]} execute-fn-sym]
  (let [tags-list (build-tags (assoc tags :function execute-fn-sym))]
    (statsd/increment jobs-recovered 1 sample-rate tags-list)))

(defn wrap-metrics
  [call]
  (fn [{{:keys [sample-rate tags]} :statsd-opts
        :as                        opts}
       {[job-type latency] :latency
        :keys              [execute-fn-sym]
        :as                job}]
    (let [tags-list (build-tags (assoc tags :function execute-fn-sym))
          start (u/epoch-time-ms)]
      (try
        (statsd/increment jobs-processed 1 sample-rate tags-list)
        ; When a job is executed using API, latency might be negative.
        ; Ignore negative values.
        (when (pos? latency)
          (statsd/timing job-type latency sample-rate tags-list))
        (call opts job)
        (statsd/increment jobs-success 1 sample-rate tags-list)
        (catch Exception ex
          (statsd/increment jobs-failure 1 sample-rate tags-list)
          (throw ex))
        (finally
          (statsd/timing execution-time (- (u/epoch-time-ms) start) sample-rate tags-list))))))

(defn- statsd-queue-metric
  [queue]
  (str "enqueued." (d/affix-queue queue) ".size"))

(defn- statsd-queues-size
  [redis-conn queues]
  (map
    (fn
      [queue]
      [(statsd-queue-metric queue)
       (r/list-size redis-conn queue)])
    queues))

(defn- get-enqueued-size
  [redis-conn]
  (let [queues (r/list-queues redis-conn)
        enqueued-list (statsd-queues-size redis-conn queues)
        enqueued-map (into {} enqueued-list)
        total-size (reduce + (vals enqueued-map))]
    (assoc enqueued-map enqueued-size total-size)))

(defn run
  [{{:keys [sample-rate tags]} :statsd-opts
    :keys                      [internal-thread-pool redis-conn process-set]}]
  (u/while-pool
    internal-thread-pool
    (u/log-on-exceptions
      (let [tags-list (build-tags (dissoc tags :queue))
            size-map {schedule-queue-size (r/sorted-set-size redis-conn d/prefixed-schedule-queue)
                      dead-queue-size     (r/sorted-set-size redis-conn d/prefixed-dead-queue)}
            process-count (heartbeat/process-count redis-conn process-set)]
        ; Using doseq instead of map, because map is lazy.
        (doseq [[k v] (merge size-map (get-enqueued-size redis-conn))]
          (statsd/gauge k v sample-rate tags-list))
        ; Sleep for (process-count) minutes + jitters.
        ; On average, Goose sends queue level stats every 1 minute.
        ; TODO: Since statsd runner sends metrics for all queues, sleep for global process-count.
        (Thread/sleep (* 1000 (+ (* 60 process-count)
                                 (rand-int process-count))))))))
