(ns goose.broker
  (:require
    [goose.defaults :as d]))

(defonce default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}})

