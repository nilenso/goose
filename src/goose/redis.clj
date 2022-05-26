(ns goose.redis
  (:require
    [goose.config :as cfg]
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

(defn scheduled-jobs-due-now [conn set]
  (wcar* conn (car/zrangebyscore set "-inf" (.getTime (java.util.Date.)) "limit" 0 cfg/scheduled-jobs-pop-limit)))

(defn enqueue-due-jobs-to-front [conn sorted-set queue-jobs-map]
  (car/atomic
    conn 100
    (let [scheduled-jobs (flatten (vals queue-jobs-map))]
      (car/multi)
      (apply car/zrem (concat [sorted-set] scheduled-jobs))
      (doseq [[queue jobs] queue-jobs-map]
        (let [prefixed-queue (str cfg/queue-prefix queue)]
          (apply car/lpush (concat [prefixed-queue] jobs)))))))
