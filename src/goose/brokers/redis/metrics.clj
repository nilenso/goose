(ns ^:no-doc goose.brokers.redis.metrics
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.metrics :as m]
    [goose.utils :as u]

    [clj-statsd]))

(defn- queue->count
  [redis-conn queues]
  (map
    (fn [queue]
      [(m/format-queue-count queue) (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- enqueued-jobs->count
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-count (queue->count redis-conn queues)
        enqueued-jobs->count (into {} queues-count)
        total-enqueued-jobs-count (reduce + (vals enqueued-jobs->count))]
    (assoc enqueued-jobs->count m/total-enqueued-jobs-count total-enqueued-jobs-count)))

(defn- protected-queue-jobs->count
  [redis-conn]
  {m/schedule-jobs-count (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
   m/dead-jobs-count     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)
   m/periodic-jobs-count (cron/size redis-conn)})

(defn- batches->count
  [redis-conn]
  {m/batches-count (count (redis-cmds/find-hashes redis-conn (str d/batch-prefix "*")))})

(defn- all-jobs->count
  [redis-conn]
  (let [jobs-in-protected-queues->count (protected-queue-jobs->count redis-conn)
        enqueued-jobs->count (enqueued-jobs->count redis-conn)
        batches->count (batches->count redis-conn)]
    (merge jobs-in-protected-queues->count enqueued-jobs->count batches->count)))

(defn run
  [{:keys [internal-thread-pool redis-conn metrics-plugin]}]
  (u/log-on-exceptions
    (when (m/enabled? metrics-plugin)
      (u/while-pool
        internal-thread-pool
        ;; Using doseq instead of map, because map is lazy.
        (doseq [[k v] (all-jobs->count redis-conn)]
          (m/gauge metrics-plugin k v {}))
        (let [global-workers-count (heartbeat/global-workers-count redis-conn)]
          ;; Sleep for (global-workers-count) minutes + jitters.
          ;; On average, Goose sends queue level stats every 1 minute.
          (u/sleep 60 global-workers-count))))))
