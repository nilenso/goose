(ns goose.statsd
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.utils :as u]

    [clj-statsd :as statsd]))

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

(defonce default-opts
         {:enabled?    true
          :host        "localhost"
          :port        8125
          :sample-rate 1.0
          :tags        {}})

(defn- build-tags
  [tags]
  (map
    (fn [[key value]] (str (name key) ":" value))
    tags))

(defn initialize
  [{:keys [enabled? host port]}]
  (when enabled?
    (statsd/setup host port :prefix prefix)))

(defn increment-recovery
  [{:keys [enabled? sample-rate tags]}
   {:keys [execute-fn-sym queue]}]
  (when enabled?
    (let [tags-list (build-tags (assoc tags :function execute-fn-sym
                                            :queue queue))]
      (statsd/increment jobs-recovered 1 sample-rate tags-list))))

(defn wrap-metrics
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
          (statsd/increment jobs-processed 1 sample-rate tags-list)
          ; When a job is executed using API, latency might be negative.
          ; Ignore negative values.
          (when (pos? latency)
            (statsd/timing job-type latency sample-rate tags-list))
          (next opts job)
          (statsd/increment jobs-success 1 sample-rate tags-list)
          (catch Exception ex
            (statsd/increment jobs-failure 1 sample-rate tags-list)
            (throw ex))
          (finally
            (statsd/timing execution-time (- (u/epoch-time-ms) start) sample-rate tags-list))))
      (next opts job))))

(defn- statsd-queue-metric
  [queue]
  (str "enqueued." (d/affix-queue queue) ".size"))

(defn- statsd-queues-size
  [redis-conn queues]
  (map
    (fn
      [queue]
      [(statsd-queue-metric queue)
       (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- get-size-of-all-queues
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-size (statsd-queues-size redis-conn queues)
        queues-size-map (into {} queues-size)
        total-size (reduce + (vals queues-size-map))]
    (assoc queues-size-map total-enqueued-size total-size)))

(defn run
  [{{:keys [enabled? sample-rate tags]} :statsd-opts
    :keys                               [internal-thread-pool redis-conn process-set]}]
  (when enabled?
    (u/while-pool
      internal-thread-pool
      (u/log-on-exceptions
        (let [tags-list (build-tags tags)
              size-map {schedule-queue-size (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
                        dead-queue-size     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)}]
          ; Using doseq instead of map, because map is lazy.
          (doseq [[k v] (merge size-map (get-size-of-all-queues redis-conn))]
            (statsd/gauge k v sample-rate tags-list))
          ; Sleep for (process-count) minutes + jitters.
          ; On average, Goose sends queue level stats every 1 minute.
          (let [process-count (heartbeat/process-count redis-conn process-set)]
            (Thread/sleep (* 1000 (+ (* 60 process-count)
                                     (rand-int process-count))))))))))
