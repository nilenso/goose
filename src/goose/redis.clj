(ns goose.redis
  (:require
    [goose.config :as cfg]
    [goose.utils :as u]
    [taoensso.carmine :as car]))

(defn conn
  [url pool-opts]
  {:pool pool-opts :spec {:uri url}})

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

(defn dequeue [conn lists]
  ; Convert list to vector to ensure timeout is last arg to blpop.
  (let [blpop-args (conj (vec lists) cfg/long-polling-timeout-sec)]
    (->> blpop-args
         (apply car/blpop)
         (wcar* conn))))

(defn enqueue-back [conn list element]
  (wcar* conn (car/rpush list element)))

(defn enqueue-front [conn list element]
  (wcar* conn (car/lpush list element)))

(defn enqueue-with-expiry [conn list element expiry-sec]
  (enqueue-back conn list element)
  (wcar* conn (car/expire list expiry-sec)))

(defn enqueue-sorted-set [conn set time element]
  (wcar* conn (car/zadd set time element)))

