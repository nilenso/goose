(ns goose.brokers.redis.commands
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(def atomic-lock-attempts 100)
(def ^:private initial-scan-cursor 0)

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

; ============ Utils =============

(defn- scan-database [conn _ cursor]
  (wcar* conn (car/scan cursor "MATCH" "*" "COUNT" 1)))

(defn- ensure-int
  [v]
  (if (string? v)
    (Integer/parseInt v)
    v))

(defn scan-seq
  "Returns a lazy seq of items scanned from Redis using a
  scan command (one of SCAN, SSCAN, HSCAN or ZSCAN).
  Passing just a connection will result in simply a SCAN over all keys.
  Pass `scan-fn` and `redis-key` for one of SSCAN, HSCAN or ZSCAN.
  `scan-fn` must take a connection, key name and cursor, call the appropriate
  scan command and return a tuple of the next cursor and the items.
  `redis-key` is the key in Redis at which the data structure is located.

  Callers may limit the amount of scans on Redis by taking a limited number of items.
  for ex: (take 5 (scan-seq conn scan-sorted-set \"my-ss\"))"
  ([conn]
   (scan-seq conn scan-database nil initial-scan-cursor))
  ([conn scan-fn]
   (scan-seq conn scan-fn nil initial-scan-cursor))
  ([conn scan-fn redis-key]
   (scan-seq conn scan-fn redis-key initial-scan-cursor))
  ([conn scan-fn redis-key cursor]
   (lazy-seq
     (let [[next-cursor-string items] (scan-fn conn redis-key cursor)
           next-cursor (ensure-int next-cursor-string)]
       (concat items
               (when-not (zero? next-cursor)
                 (scan-seq conn scan-fn redis-key next-cursor)))))))

(defn run-with-transaction
  "Runs fn inside a Carmine atomic block, and returns
  whatever fn returns."
  [redis-conn f]
  (let [return-value (atom nil)]
    (car/atomic redis-conn atomic-lock-attempts
      ;; This ugliness is necessary because car/atomic does not return the value
      ;; of the last expression inside it.
      (reset! return-value (f)))
    @return-value))

(defmacro with-transaction
  "Runs `body` inside a Carmine `atomic` block.
  `body` must call `car/multi`."
  [redis-conn & body]
  `(run-with-transaction ~redis-conn
                         (fn [] ~@body)))

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

(defn set-seq [conn set-key]
  (scan-seq conn
            (fn [conn redis-key cursor]
              (let [count 1]
                (scan-set conn redis-key cursor count)))
            set-key))

(defn find-sets
  [conn match-str]
  (let [scan-fn (fn [conn _ cursor]
                  (let [count 1]
                    (scan-for-sets conn cursor match-str count)))]
    (doall (scan-seq conn scan-fn))))

(defn find-in-set
  [conn set match?]
  (->> (set-seq conn set)
       (filter match?)
       (doall)))

; ============== Lists ===============
; ===== FRONT/BACK -> RIGHT/LEFT =====
(defn enqueue-back
  ([conn list element]
   (wcar* conn (car/lpush list element))))

(defn enqueue-front [conn list element]
  (wcar* conn (car/rpush list element)))

(defn dequeue-and-preserve [conn src dst]
  ; `RPOPLPUSH` will be deprecated soon.
  ; Switch to `LMOVE` as soon as Carmine supports that.
  ; https://github.com/ptaoussanis/carmine/issues/268
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
  (let [scan-fn (fn [conn _ cursor]
                  (let [count 1]
                    (scan-for-lists conn cursor match-str count)))]
    (doall (scan-seq conn scan-fn))))

(defn list-seq
  "Returns a lazy sequence of a list which iterates
  through its elements one at a time, from right to left."
  [conn list-key]
  (let [size (list-size conn list-key)
        scan-fn (fn [conn redis-key cursor]
                  ;; We iterate from the end of the list down to index zero,
                  ;; since lists in Goose represent queues,
                  ;; and the front of a queue is the right side (the tail).
                  (let [next-cursor (dec cursor)
                        elements (wcar* conn
                                   (car/lrange redis-key (dec cursor) (dec cursor)))]
                    [next-cursor elements]))]
    (scan-seq conn scan-fn list-key size)))

(defn find-in-list
  [conn queue match? limit]
  (->> (list-seq conn queue)
       (filter match?)
       (take limit)
       (doall)))

; ============ Sorted-Sets ============
(def sorted-set-min "-inf")
(def sorted-set-max "+inf")

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

(defn move-jobs-from-sorted-set-to-ready-queue [conn sorted-set jobs grouping-fn]
  (car/atomic
    conn atomic-lock-attempts
    (car/multi)
    (doseq [[queue jobs] (group-by grouping-fn jobs)]
      (apply car/rpush queue jobs))
    (apply car/zrem sorted-set jobs)))

(defn sorted-set-score [conn sorted-set element]
  (wcar* conn (car/zscore sorted-set element)))

(defn sorted-set-size [conn sorted-set]
  (wcar* conn (car/zcount sorted-set sorted-set-min sorted-set-max)))

(defn- scan-sorted-set [conn sorted-set cursor]
  (let [[next-cursor-string replies] (wcar* conn (car/zscan sorted-set cursor "MATCH" "*" "COUNT" 1))]
    [next-cursor-string (map first (partition 2 replies))]))

(defn sorted-set-seq [conn sorted-set-key]
  (scan-seq conn scan-sorted-set sorted-set-key))

(defn sorted-set-pop-from-head
  "Utility function to pop from head of dead-jobs queue.
  Job with lowest score will be considered as head of the queue."
  [conn sorted-set]
  (wcar* conn (car/zpopmin sorted-set)))

(defn find-in-sorted-set
  [conn sorted-set match? limit]
  (->> (sorted-set-seq conn sorted-set)
       (filter match?)
       (take limit)
       (doall)))

(defn del-from-sorted-set [conn sorted-set member]
  (wcar* conn (car/zrem sorted-set member)))

(defn del-from-sorted-set-until [conn sorted-set score]
  (wcar* conn (car/zremrangebyscore sorted-set sorted-set-min score)))
