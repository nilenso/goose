(ns goose.brokers.redis.connection)

(defn new
  [url pool-opts]
  {:spec {:uri url} :pool pool-opts})
