(ns goose.redis
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(def ^:private atomic-lock-attempts 100)
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

(defn scan-sets [conn set cursor count]
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

(defn scan-for-lists [conn cursor match count]
  (wcar* conn (car/scan cursor "MATCH" match "COUNT" count "TYPE" "LIST")))

(defn- fetch-queues
  ([conn]
   (fetch-queues conn '() d/scan-initial-cursor))
  ([conn queues cursor]
   (let [match-str (str d/queue-prefix "*")
         [next scanned-queues] (scan-for-lists conn cursor match-str 1)
         queues (concat queues scanned-queues)]
     (if (= next d/scan-initial-cursor)
       queues
       #(fetch-queues conn queues next)))))

(defn list-queues
  [conn]
  (trampoline fetch-queues conn))

(defn iterate-list
  [conn queue match? limit jobs index]
  (if (neg? index)
    jobs
    (let [job (first (wcar* conn (car/lrange queue index index)))]
      (if (match? job)
        (let [jobs (conj jobs job)]
          (if (>= (count jobs) limit)
            jobs
            #(iterate-list conn queue match? limit jobs (dec index))))
        #(iterate-list conn queue match? limit jobs (dec index))))))

(defn find-in-list
  ([conn queue match? limit]
   (trampoline iterate-list conn queue match? limit '() (dec (list-size conn queue)))))

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

(defn- iterate-sorted-set
  [conn sorted-set match? limit jobs cursor]
  (let
    [[next scanned-pairs] (scan-sorted-set conn sorted-set cursor "*" 1)
     scanned-jobs (take-nth 2 scanned-pairs)
     matched-jobs (filter match? scanned-jobs)
     jobs (concat jobs matched-jobs)]
    (if (or (>= (count jobs) limit)
            (= next d/scan-initial-cursor))
      jobs
      #(iterate-sorted-set conn sorted-set match? limit jobs next))))

(defn find-in-sorted-set
  ([conn sorted-set match? limit]
   (trampoline iterate-sorted-set conn sorted-set match? limit '() d/scan-initial-cursor)))

(defn del-from-sorted-set [conn sorted-set member]
  (wcar* conn (car/zrem sorted-set member)))

(defn del-from-sorted-set-until [conn sorted-set score]
  (wcar* conn (car/zremrangebyscore sorted-set sorted-set-min score)))
