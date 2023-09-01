(ns ^:no-doc goose.brokers.redis.metrics
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.metrics :as m]
    [goose.utils :as u]

    [clj-statsd]))

(defn- get-queues-count-for-metrics
  [redis-conn queues]
  (map
    (fn [queue]
      [(m/format-queue-count queue) (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- get-count-of-enqueued-jobs
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-count (get-queues-count-for-metrics redis-conn queues)
        enqueued-jobs->count (into {} queues-count)
        total-enqueued-jobs-count (reduce + (vals enqueued-jobs->count))]
    (assoc enqueued-jobs->count m/total-enqueued-jobs-count total-enqueued-jobs-count)))

(defn- get-count-of-jobs-in-protected-queues
  [redis-conn]
  {m/schedule-jobs-count (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
   m/dead-jobs-count     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)
   m/periodic-jobs-count (cron/size redis-conn)})

(defn- get-count-of-all-batches
  [redis-conn]
  {m/batches-count (count (redis-cmds/find-hashes redis-conn (str d/batch-prefix "*")))})

(defn- get-count-of-all-job-types
  [redis-conn]
  (let [jobs-in-protected-queues->count (get-count-of-jobs-in-protected-queues redis-conn)
        enqueued-jobs->count (get-count-of-enqueued-jobs redis-conn)
        batches->count (get-count-of-all-batches redis-conn)]
    (merge jobs-in-protected-queues->count enqueued-jobs->count batches->count)))

(defn run
  [{:keys [internal-thread-pool redis-conn metrics-plugin]}]
  (u/log-on-exceptions
    (when (m/enabled? metrics-plugin)
      (u/while-pool
        internal-thread-pool
        (doseq [[k v] (get-count-of-all-job-types redis-conn)]
          (m/gauge metrics-plugin k v {}))
        (let [global-workers-count (heartbeat/global-workers-count redis-conn)]
          ;; Sleep for (global-workers-count) minutes + jitters.
          ;; On average, Goose sends queue level stats every 1 minute.
          (u/sleep 60 global-workers-count))))))
