(ns goose.brokers.redis.commands
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(def ^:private atomic-lock-attempts 100)

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

; ============ Utils =============

(defn iterate-redis
  ([conn iterate-fn match? stop? cursor]
   (iterate-redis conn iterate-fn match? stop? cursor '()))
  ([conn iterate-fn match? stop? cursor elements]
   (let [[next scanned-elements] (iterate-fn cursor)
         filtered-elements (filter match? scanned-elements)
         elements (concat elements filtered-elements)]
     (if (stop? next (count elements))
       elements
       #(iterate-redis conn iterate-fn match? stop? next elements)))))

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

(defn- scan-for-sets [conn cursor match count]
  (wcar* conn (car/scan cursor "MATCH" match "COUNT" count "TYPE" "SET")))

(defn find-sets
  [conn match-str]
  (let [iterate-fn (fn [cursor] (scan-for-sets conn cursor match-str 1))
        stop? (fn [cursor _] (= cursor d/scan-initial-cursor))]
    (trampoline iterate-redis conn iterate-fn identity stop? d/scan-initial-cursor)))

(defn find-in-set
  [conn set match?]
  (let [iterate-fn (fn [cursor] (scan-set conn set cursor 1))
        stop? (fn [cursor _] (= cursor d/scan-initial-cursor))]
    (trampoline iterate-redis conn iterate-fn match? stop? d/scan-initial-cursor)))

; ============== Lists ===============
; ===== FRONT/BACK -> RIGHT/LEFT =====
(defn enqueue-back
  ([conn list element]
   (wcar* conn (car/lpush list element))))

(defn enqueue-front [conn list element]
  (wcar* conn (car/rpush list element)))

(defn dequeue-and-preserve [conn src dst]
  (wcar* conn (car/brpoplpush src dst d/long-polling-timeout-sec)))

(defn list-position [conn list element]
  (wcar* conn (car/lpos list element) "COUNT" 1))

(defn del-from-list-and-enqueue-front [conn list element]
  (car/atomic
    conn atomic-lock-attempts
    (car/multi)
    (car/lrem list 1 element)
    (car/rpush list element)))

(defn del-from-list [conn list element]
  (wcar* conn (car/lrem list 1 element)))

(defn list-size [conn list]
  (wcar* conn (car/llen list)))

(defn- scan-for-lists [conn cursor match count]
  (wcar* conn (car/scan cursor "MATCH" match "COUNT" count "TYPE" "LIST")))

(defn find-lists
  [conn match-str]
  (let [iterate-fn (fn [cursor] (scan-for-lists conn cursor match-str 1))
        stop? (fn [cursor _] (= cursor d/scan-initial-cursor))]
    (trampoline iterate-redis conn iterate-fn identity stop? d/scan-initial-cursor)))

(defn find-in-list
  ([conn queue match? limit]
   (let [iterate-fn (fn [index]
                      (when (< -1 index)
                        [(dec index) (wcar* conn (car/lrange queue index index))]))
         stop? (fn [index count]
                 (if index
                   (or (neg? index) (>= count limit))
                   true))]
     (trampoline iterate-redis conn iterate-fn match? stop? (dec (list-size conn queue))))))

; ============ Sorted-Sets ============
(def ^:private sorted-set-min "-inf")
(def ^:private sorted-set-max "+inf")

(defn enqueue-sorted-set [conn sorted-set score element]
  (wcar* conn (car/zadd sorted-set score element)))

(defn scheduled-jobs-due-now [conn sorted-set]
  (let [limit "limit"
        offset 0]
    (not-empty
      (wcar*
        conn
        (car/zrangebyscore
          sorted-set sorted-set-min (u/epoch-time-ms)
          limit offset d/scheduled-jobs-pop-limit)))))

(defn enqueue-due-jobs-to-front [conn sorted-set jobs grouping-fn]
  (car/atomic
    conn atomic-lock-attempts
    (car/multi)
    (apply car/zrem sorted-set jobs)
    (doseq [[queue jobs] (group-by grouping-fn jobs)]
      (apply car/rpush queue jobs))))

(defn sorted-set-score [conn sorted-set element]
  (wcar* conn (car/zscore sorted-set element)))

(defn sorted-set-size [conn sorted-set]
  (wcar* conn (car/zcount sorted-set sorted-set-min sorted-set-max)))

(defn scan-sorted-set [conn sorted-set cursor match count]
  (wcar* conn (car/zscan sorted-set cursor "MATCH" match "COUNT" count)))

(defn find-in-sorted-set
  ([conn sorted-set match? limit]
   (let [iterate-fn (fn [cursor]
                      (let [[next scanned-pairs] (scan-sorted-set conn sorted-set cursor "*" 1)
                            scanned-jobs (take-nth 2 scanned-pairs)]
                        [next scanned-jobs]))
         stop? (fn [next count]
                 (or (>= count limit)
                     (= next d/scan-initial-cursor)))]
     (trampoline iterate-redis conn iterate-fn match? stop? d/scan-initial-cursor))))

(defn del-from-sorted-set [conn sorted-set member]
  (wcar* conn (car/zrem sorted-set member)))

(defn del-from-sorted-set-until [conn sorted-set score]
  (wcar* conn (car/zremrangebyscore sorted-set sorted-set-min score)))
