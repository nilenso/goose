(ns goose.api.queues
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]))

(defn list-all
  [{:keys [redis-conn]}]
  (map d/affix-queue (r/list-queues redis-conn)))

(defn size
  [{:keys [redis-conn]} queue]
  (r/list-size redis-conn (d/prefix-queue queue)))

(defn clear
  [{:keys [redis-conn]} queue]
  (= 1 (r/del-keys redis-conn [(d/prefix-queue queue)])))
