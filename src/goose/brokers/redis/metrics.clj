(ns goose.brokers.redis.metrics
  ^:no-doc
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.metrics :as m]
    [goose.utils :as u]

    [clj-statsd]))

(defn- statsd-queues-size
  [redis-conn queues]
  (map
    (fn [queue]
      [(m/format-queue-size queue) (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- get-size-of-all-queues
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-size (statsd-queues-size redis-conn queues)
        queues->size (into {} queues-size)
        total-size (reduce + (vals queues->size))]
    (assoc queues->size m/total-enqueued-size total-size)))

(defn- get-size-of-protected-queues
  [redis-conn]
  {m/schedule-queue-size (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
   m/dead-queue-size     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)
   m/periodic-jobs-size  (cron/size redis-conn)})

(defn run
  [{:keys [internal-thread-pool redis-conn metrics-plugin]}]
  (u/log-on-exceptions
    (when (m/enabled? metrics-plugin)
      (u/while-pool
        internal-thread-pool
        (let [protected-queues->size (get-size-of-protected-queues redis-conn)
              queues->size (get-size-of-all-queues redis-conn)]
          ;; Using doseq instead of map, because map is lazy.
          (doseq [[k v] (merge protected-queues->size queues->size)]
            (m/gauge metrics-plugin k v {})))
        (let [global-workers-count (heartbeat/global-workers-count redis-conn)]
          ;; Sleep for (global-workers-count) minutes + jitters.
          ;; On average, Goose sends queue level stats every 1 minute.
          (u/sleep 60 global-workers-count))))))
