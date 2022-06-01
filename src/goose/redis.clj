(ns goose.redis
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(defn conn
  [url pool-opts]
  {:pool pool-opts :spec {:uri url}})

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

(defn dequeue [conn lists]
  ; Convert list to vector to ensure timeout is last arg to blpop.
  (let [blpop-args (conj (vec lists) d/long-polling-timeout-sec)]
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

(defn enqueue-sorted-set [conn sorted-set score element]
  (wcar* conn (car/zadd sorted-set score element)))

(defn scheduled-jobs-due-now [conn sorted-set]
  (let [min "-inf"
        limit "limit"
        offset 0]
    (not-empty
      (wcar*
        conn
        (car/zrangebyscore
          sorted-set min (u/epoch-time-ms)
          limit offset d/scheduled-jobs-pop-limit)))))

(defn- appropriate-queue
  [job]
  (if (get-in job [:retry-opts :error])
    (get-in job [:retry-opts :retry-queue])
    (:queue job)))

(defn enqueue-due-jobs-to-front [conn sorted-set jobs]
  (let [cas-attempts 100]
    (car/atomic
      conn cas-attempts
      (car/multi)
      (apply car/zrem sorted-set jobs)
      (doseq [[queue jobs] (group-by appropriate-queue jobs)]
        (apply car/lpush queue jobs)))))
