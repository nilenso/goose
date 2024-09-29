(ns ^:no-doc goose.brokers.redis.api.dead-jobs
  (:refer-clojure :exclude [pop])
  (:require
   [goose.brokers.redis.commands :as redis-cmds]
   [goose.defaults :as d]
   [goose.job :as job]))

(defn size [redis-conn]
  (redis-cmds/sorted-set-size redis-conn d/prefixed-dead-queue))

(defn pop
  [redis-conn]
  (let [[job _] (redis-cmds/sorted-set-pop-from-head redis-conn d/prefixed-dead-queue)]
    job))

(defn find-by-pattern [redis-conn match? limit]
  (redis-cmds/find-in-sorted-set redis-conn d/prefixed-dead-queue match? limit))

(defn find-by-id [redis-conn id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern redis-conn match? limit))))

;; TODO: Return job instead of redis txn
(defn replay-job [redis-conn job]
  (let [sorted-set d/prefixed-dead-queue]
    (when (every? (comp not nil?) (redis-cmds/sorted-set-scores redis-conn sorted-set job))
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set (list job) job/ready-or-retry-queue))))

;; Used internally by console
(defn replay-jobs [redis-conn & jobs]
  (let [sorted-set d/prefixed-dead-queue
        scores (apply redis-cmds/sorted-set-scores redis-conn sorted-set jobs)
        jobs-with-valid-scores (->> (mapv vector jobs scores)
                                    (remove #(nil? (second %)))
                                    (mapv first))
        valid-jobs-count (count jobs-with-valid-scores)]
    (when (> valid-jobs-count 0)
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set jobs-with-valid-scores job/ready-or-retry-queue))
    jobs-with-valid-scores))

(defn replay-n-jobs [redis-conn n]
  (let [sorted-set d/prefixed-dead-queue
        jobs (redis-cmds/sorted-set-peek-jobs redis-conn sorted-set n)]
    (when (< 0 (count jobs))
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set jobs job/ready-or-retry-queue))
    (count jobs)))

(defn delete [redis-conn & jobs]
  (= (count jobs) (apply redis-cmds/del-from-sorted-set redis-conn d/prefixed-dead-queue jobs)))

(defn delete-older-than [redis-conn epoch-ms]
  (< 0 (redis-cmds/del-from-sorted-set-until
        redis-conn d/prefixed-dead-queue epoch-ms)))

(defn purge [redis-conn]
  (= 1 (redis-cmds/del-keys redis-conn d/prefixed-dead-queue)))

(defn get-by-range
  [redis-conn start stop]
  (redis-cmds/rev-range-in-sorted-set redis-conn d/prefixed-dead-queue start stop))
