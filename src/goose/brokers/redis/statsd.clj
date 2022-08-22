(ns goose.brokers.redis.statsd
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.statsd :as statsd]
    [goose.utils :as u]

    [clj-statsd]))

(defn- statsd-queues-size
  [redis-conn queues]
  (map
    (fn
      [queue]
      [(statsd/statsd-queue-metric queue)
       (redis-cmds/list-size redis-conn queue)])
    queues))

(defn- get-size-of-all-queues
  [redis-conn]
  (let [queues (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))
        queues-size (statsd-queues-size redis-conn queues)
        queues-size-map (into {} queues-size)
        total-size (reduce + (vals queues-size-map))]
    (assoc queues-size-map statsd/total-enqueued-size total-size)))

(defn ^:no-doc run
  [{{:keys [enabled? sample-rate tags]} :statsd-opts
    :keys                               [internal-thread-pool redis-conn]}]
  (when enabled?
    (u/while-pool
      internal-thread-pool
      (u/log-on-exceptions
        (let [tags-list (statsd/build-tags tags)
              size-map {statsd/schedule-queue-size (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue)
                        statsd/dead-queue-size     (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue)}]
          ; Using doseq instead of map, because map is lazy.
          (doseq [[k v] (merge size-map (get-size-of-all-queues redis-conn))]
            (clj-statsd/gauge k v sample-rate tags-list)))
        (let [total-process-count (heartbeat/total-process-count redis-conn)]
          ; Sleep for total-process-count minutes + jitters.
          ; On average, Goose sends queue level stats every 1 minute.
          (Thread/sleep (* 1 (+ (* 60 total-process-count)
                                (rand-int total-process-count)))))))))

