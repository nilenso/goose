(ns goose.brokers.redis.client
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.worker :as worker]
    [goose.defaults :as d]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.scheduler :as redis-scheduler]))

(def default-opts
  "Default config for Redis client."
  {:type d/redis
   :url  d/redis-default-url})

(defn- new-pool-opts
  [thread-count]
  (if thread-count
    {:max-total-per-key (+ d/redis-internal-thread-pool-size thread-count)
     :max-idle-per-key  (+ d/redis-internal-thread-pool-size thread-count)
     :min-idle-per-key  (inc d/redis-internal-thread-pool-size)}
    {:max-total-per-key d/redis-client-pool-size
     :max-idle-per-key  d/redis-client-pool-size
     :min-idle-per-key  1}))

(defrecord Redis [conn]
  b/Broker
  (enqueue [this job]
    (redis-cmds/enqueue-back (:conn this) (:prefixed-queue job) job))
  (schedule [this schedule job]
    (redis-scheduler/run-at (:conn this) schedule job))
  (start [this worker-opts]
    (worker/start (assoc worker-opts :redis-conn (:conn this)))))

(defmethod b/new d/redis new-redis-client
  ([opts] (b/new opts nil))
  ([{:keys [url pool-opts]} thread-count]
   (let [pool-opts (or pool-opts (new-pool-opts thread-count))]
     (Redis. {:spec {:uri url} :pool pool-opts}))))
