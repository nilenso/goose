(ns goose.redis
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(defn conn
  [{:keys [redis-url redis-pool-opts]}]
  {:pool redis-pool-opts :spec {:uri redis-url}})

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

; ============ Key-Value =============
(defn set-key-val [conn key value expire-sec]
  (wcar* conn (car/set key value "EX" expire-sec)))

(defn get-key [conn key]
  (wcar* conn (car/get key)))

(defn del-keys [conn keys]
  (wcar* conn (apply car/del keys)))

; ============== Sets ===============
(defn add-to-set [conn set member]
  (wcar* conn (car/sadd set member)))

(defn del-from-set [conn set member]
  (wcar* conn (car/srem set member)))

(defn scan-set [conn set cursor count]
  (wcar* conn (car/sscan set cursor "COUNT" count)))

(defn set-size [conn set]
  (wcar* conn (car/scard set)))

; ============== Lists ===============
; ===== FRONT/BACK -> RIGHT/LEFT =====
(defn enqueue-back
  ([conn list element]
   (wcar* conn (car/lpush list element))))

(defn enqueue-front [conn list element]
  (wcar* conn (car/rpush list element)))

(defn dequeue-and-preserve [conn src dst]
  (wcar* conn (car/brpoplpush src dst d/long-polling-timeout-sec)))

(defn remove-from-list [conn list element]
  (wcar* conn (car/lrem list 1 element)))

(defn list-size [conn list]
  (wcar* conn (car/llen list)))

; ============ Sorted-Sets ============
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

(defn enqueue-due-jobs-to-front [conn sorted-set jobs grouping-fn]
  (let [cas-attempts 100]
    (car/atomic
      conn cas-attempts
      (car/multi)
      (apply car/zrem sorted-set jobs)
      (doseq [[queue jobs] (group-by grouping-fn jobs)]
        (apply car/rpush queue jobs)))))

(defn sorted-set-size [conn sorted-set]
  (wcar* conn (car/zcount sorted-set "-inf" "+inf")))
