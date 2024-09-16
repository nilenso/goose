(ns ^:no-doc goose.brokers.redis.commands
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(def atomic-lock-attempts 100)
(def ^:private initial-scan-cursor 0)

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

;;; ============ Utils =============

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
               (when (pos? next-cursor)
                 (scan-seq conn scan-fn redis-key next-cursor)))))))

(defmacro atomic
  "A simple wrapper over Carmine's `atomic` macro.
  Use this wrapper instead of `run-with-transaction` when you
  need values returned by redis, and not surrounding function."
  [conn & body]
  `(car/atomic ~conn
     atomic-lock-attempts
     ~@body))

(defn run-with-transaction
  "Runs fn inside a Carmine atomic block, and returns
  whatever fn returns."
  [conn f]
  (let [return-value (atom nil)]
    (car/atomic conn atomic-lock-attempts
      ;; This ugliness is necessary because car/atomic does not
      ;; return the value of the last expression inside it.
      (reset! return-value (f)))
    @return-value))

(defmacro with-transaction
  "Runs `body` inside a Carmine `atomic` block.
  `body` must call `car/multi`."
  [conn & body]
  `(run-with-transaction ~conn
                         (fn [] ~@body)))

;;; ============ Key-Value =============
(defn set-key-val [conn key value expire-sec]
  (wcar* conn (car/set key value "EX" expire-sec)))

(defn get-key [conn key]
  (wcar* conn (car/get key)))

(defn del-keys [conn key & keys]
  (wcar* conn (apply car/del key keys)))

;;; ============== Sets ===============
(defn add-to-set [conn set member]
  (wcar* conn (car/sadd set member)))

(defn del-from-set [conn set member]
  (wcar* conn (car/srem set member)))

(defn set-size [conn set]
  (wcar* conn (car/scard set)))

(defn set-members [conn key]
  (->> (wcar* conn (car/smembers key))
       (set)))

(defn scan-set [conn set cursor count]
  (wcar* conn (car/sscan set cursor "COUNT" count)))

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

;;; ============== Lists ===============
;;; ===== FRONT/BACK -> RIGHT/LEFT =====
(defn enqueue-back [conn list element]
  (wcar* conn (car/lpush list element)))

(defn enqueue-front [conn list element]
  (wcar* conn (car/rpush list element)))

(defn dequeue-and-preserve [conn src dst]
  ;; Whenever moving an element between lists,
  ;; Goose pops from head of source list and
  ;; pushes to head of destination list.

  ;; Carmine swallows interrupted exception, hence polling timeout
  ;; is too short, i.e. 1 sec. Switch to higher timeout when
  ;; client library doesn't swallow exception.
  ;; https://github.com/ptaoussanis/carmine/issues/266
  (wcar* conn (car/blmove src dst "RIGHT" "RIGHT" d/redis-long-polling-timeout-sec)))

(defn del-from-list-and-enqueue-front [conn list & elements]
  (->> (wcar* conn :as-pipeline (doseq [element elements]
                                  (car/multi)
                                  (car/lrem list 1 element)
                                  (car/rpush list element)
                                  (car/exec)))
       ;; To ensure consistency with the contract for handling individual element responses(used earlier using
       ;; car/atomic), the response format for multiple elements is constructed as sequence of these single element
       ;; responses.

       ;; the response is a flattened list containing result of individual commands
       ;; ex i/p: ["OK" "QUEUED" "QUEUED" [0 1] "OK" "QUEUED" "QUEUED" [0 1]]
       ;; the final response is partitioned into vectors with 4 results (i.e response for 1 `element`)
       ;; ex o/p: [[["OK" "QUEUED" "QUEUED"] [0 1]] [["OK" "QUEUED" "QUEUED"] [0 1]]]
       (partition 4)
       (mapv #(conj [] (vec (butlast %)) (last %)))))

(defn list-position [conn list & elements]
  (wcar* conn :as-pipeline (doseq [e elements]
                             (car/lpos list e))))

(defn del-from-list [conn list & elements]
  (wcar* conn :as-pipeline (doseq [e elements]
                             (car/lrem list 1 e))))

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

(defn range-from-front
  "Fetches range of elements from front where start and stop positions are relative to the front of list"
  [conn list-key start stop]
  (let [begin (- (* stop -1) 1)
        end (- (* start -1) 1)]
    (reverse (wcar* conn (car/lrange list-key begin end)))))

(defn find-in-list
  [conn queue match? limit]
  (->> (list-seq conn queue)
       (filter match?)
       (take limit)
       (doall)))

;;; ============ Sorted-Sets ============
(def sorted-set-min "-inf")
(def sorted-set-max "+inf")

(defn enqueue-sorted-set [conn sorted-set score element]
  (wcar* conn (car/zadd sorted-set score element)))

(defn scheduled-jobs-due-now
  [conn sorted-set]
  (let [limit "limit"
        offset 0]
    (not-empty
      (wcar*
        conn
        (car/zrangebyscore
          sorted-set sorted-set-min (u/epoch-time-ms)
          limit offset d/redis-scheduled-jobs-pop-limit)))))

(defn sorted-set->ready-queue
  [conn sorted-set jobs grouping-fn]
  (car/atomic
    conn atomic-lock-attempts
    (car/multi)
    (doseq [[queue jobs] (group-by grouping-fn jobs)]
      (apply car/rpush queue jobs))
    (apply car/zrem sorted-set jobs)))

(defn sorted-set-scores [conn sorted-set & elements]
  (wcar* conn (apply car/zmscore sorted-set elements)))

(defn sorted-set-size [conn sorted-set]
  (wcar* conn (car/zcount sorted-set sorted-set-min sorted-set-max)))

(defn- scan-sorted-set
  [conn sorted-set cursor]
  (let [[next-cursor-string replies] (wcar* conn (car/zscan sorted-set cursor "MATCH" "*" "COUNT" 1))]
    [next-cursor-string (map first (partition 2 replies))]))

(defn sorted-set-seq [conn sorted-set-key]
  (scan-seq conn scan-sorted-set sorted-set-key))

(defn sorted-set-pop-from-head
  "Utility function to pop from head of dead-jobs queue.
  Job with lowest score will be considered as head of the queue."
  [conn sorted-set]
  (wcar* conn (car/zpopmin sorted-set)))

(defn sorted-set-peek-jobs
  "Return n jobs with lowest score"
  [conn sorted-set n]
  (wcar* conn (car/zrange sorted-set 0 (dec n))))

(defn rev-range-in-sorted-set
  [conn sorted-set start stop]
  (wcar* conn (car/zrevrange sorted-set start stop)))

(defn range-in-sorted-set
  [conn sorted-set start stop]
  (wcar* conn (car/zrange sorted-set start stop)))

(defn find-in-sorted-set
  [conn sorted-set match? limit]
  (->> (sorted-set-seq conn sorted-set)
       (filter match?)
       (take limit)
       (doall)))

(defn del-from-sorted-set [conn sorted-set & members]
  (wcar* conn (apply car/zrem sorted-set members)))

(defn del-from-sorted-set-until [conn sorted-set score]
  (wcar* conn (car/zremrangebyscore sorted-set sorted-set-min score)))

;;; ============ Hashes ============
(defn- scan-for-hashes [conn cursor match count]
  (wcar* conn (car/scan cursor "MATCH" match "COUNT" count "TYPE" "HASH")))

(defn find-hashes
  [conn match-str]
  (let [scan-fn (fn [conn _ cursor]
                  (let [count 1]
                    (scan-for-hashes conn cursor match-str count)))]
    (doall (scan-seq conn scan-fn))))

(defn get-all-values
  [conn key]
  (wcar* conn (car/hvals key)))
