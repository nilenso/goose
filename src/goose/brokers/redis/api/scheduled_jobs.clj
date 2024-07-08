(ns ^:no-doc goose.brokers.redis.api.scheduled-jobs
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]))

(defn size [redis-conn]
  (redis-cmds/sorted-set-size redis-conn d/prefixed-schedule-queue))

(defn find-by-pattern [redis-conn match? limit]
  (redis-cmds/find-in-sorted-set redis-conn d/prefixed-schedule-queue match? limit))

(defn find-by-id [redis-conn id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern redis-conn match? limit))))

(defn prioritise-execution [redis-conn job]
  (let [sorted-set d/prefixed-schedule-queue]
    (when (every? (comp not nil?) (redis-cmds/sorted-set-scores redis-conn sorted-set job))
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set (list job) job/ready-or-retry-queue))))

;; Inorder to continue support for existing API for prioritise-execution
;; above fn is not modified, instead new fn is introduced to be used by console
;; which returns jobs that were successfully prioritised instead of redis-txn
(defn prioritises-execution [redis-conn & jobs]
  (let [sorted-set d/prefixed-schedule-queue
        scores (apply redis-cmds/sorted-set-scores redis-conn sorted-set jobs)
        jobs-with-valid-scores (->> (mapv vector jobs scores)
                                    (remove #(nil? (second %)))
                                    (mapv first))]
    (when (> (count jobs-with-valid-scores) 0)
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set jobs job/ready-or-retry-queue))
    jobs-with-valid-scores))

(defn delete [redis-conn & jobs]
  (= (count jobs) (apply redis-cmds/del-from-sorted-set redis-conn d/prefixed-schedule-queue jobs)))

(defn purge [redis-conn]
  (= 1 (redis-cmds/del-keys redis-conn d/prefixed-schedule-queue)))

;;Get scheduled jobs in increase order of their schedule-run-at i.e score
(defn get-by-range
  [redis-conn start stop]
  (redis-cmds/range-in-sorted-set redis-conn d/prefixed-schedule-queue start stop))
