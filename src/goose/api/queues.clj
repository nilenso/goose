(ns goose.api.queues
  (:require
    [goose.api.init :as init]
    [goose.defaults :as d]
    [goose.redis :as r]))

(defn list-all []
  (map d/affix-queue (r/list-queues @init/broker-conn)))

(defn size
  [queue]
  (r/list-size @init/broker-conn (d/prefix-queue queue)))

(defn clear
  [queue]
  (= 1 (r/del-keys @init/broker-conn [(d/prefix-queue queue)])))
