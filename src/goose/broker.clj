(ns goose.broker
  (:require
    [goose.defaults :as d]))

(def default-opts
  {:type            "redis"
   :redis-url       d/default-redis-url
   :redis-pool-opts {}})

(defn enhance-opts
  [opts]
  (merge default-opts opts))
