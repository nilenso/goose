(ns goose.broker
  (:require
    [goose.defaults :as d]))

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}})

