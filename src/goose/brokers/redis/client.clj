(ns goose.brokers.redis.client
  (:require
    [goose.brokers.broker :as broker]
    [goose.defaults :as d]))

(defonce default-opts
         {:type d/redis
          :url  d/default-redis-url})

(defn- new-pool-opts
  [thread-count]
  (if thread-count
    {:max-total-per-key (+ d/internal-thread-pool-size thread-count)
     :max-idle-per-key  (+ d/internal-thread-pool-size thread-count)
     :min-idle-per-key  (inc d/internal-thread-pool-size)}
    {:max-total-per-key d/client-redis-pool-size
     :max-idle-per-key  d/client-redis-pool-size
     :min-idle-per-key  1}))

(defmethod broker/new d/redis
  ([opts] (broker/new opts nil))
  ([{:keys [url pool-opts]} thread-count]
   (let [pool-opts (or pool-opts (new-pool-opts thread-count))]
     {:spec {:uri url} :pool pool-opts})))
