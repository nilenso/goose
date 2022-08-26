(ns goose.brokers.redis.metrics
  {:no-doc true}
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.metrics.keys :as metrics-keys]
    [goose.metrics.protocol :as metrics-protocol]
    [goose.utils :as u]

    [clj-statsd]))

(defn- statsd-queues-size
  [redis-conn queues]
  (map
    (fn
      [queue]
      [(metrics-keys/queue-size queue)
       (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- get-size-of-all-queues
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-size (statsd-queues-size redis-conn queues)
        queues-size-map (into {} queues-size)
        total-size (reduce + (vals queues-size-map))]
    (assoc queues-size-map metrics-keys/total-enqueued-size total-size)))

(defn run
  [{:keys [internal-thread-pool redis-conn metrics-plugin]}]
  (u/log-on-exceptions
    (when (metrics-protocol/enabled? metrics-plugin)
      (u/while-pool
        internal-thread-pool
        (let [size-map {metrics-keys/schedule-queue-size (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
                        metrics-keys/dead-queue-size     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)}]
          ; Using doseq instead of map, because map is lazy.
          (doseq [[k v] (merge size-map (get-size-of-all-queues redis-conn))]
            (metrics-protocol/gauge metrics-plugin k v {})))
        (let [total-process-count (heartbeat/total-process-count redis-conn)]
          ; Sleep for total-process-count minutes + jitters.
          ; On average, Goose sends queue level stats every 1 minute.
          (Thread/sleep (* 1000 (+ (* 60 total-process-count)
                                (rand-int total-process-count)))))))))

