(ns goose.brokers.redis
  (:require
    [goose.defaults :as d]))

(defonce default-opts {:redis-url d/default-redis-url})

(defn- construct-pool-opts
  [url thread-count]
  (let [redis-opts {:redis-url url}]
    (if thread-count
      (assoc redis-opts
        :redis-pool-opts {:max-total-per-key (+ d/internal-thread-pool-size thread-count)
                          :max-idle-per-key  (+ d/internal-thread-pool-size thread-count)
                          :min-idle-per-key d/internal-thread-pool-size})
      (assoc redis-opts
        :redis-pool-opts {:max-total-per-key d/client-redis-pool-size
                          :max-idle-per-key  d/client-redis-pool-size
                          :min-idle-per-key 1}))))

(defn new
  [{:keys [redis-url redis-pool-opts] :as opts}
   thread-count]
  (if redis-pool-opts
    opts
    (construct-pool-opts redis-url thread-count)))
