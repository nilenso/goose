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
    (when (redis-cmds/sorted-set-score redis-conn sorted-set job)
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set (list job) job/ready-or-retry-queue))))

(defn delete [redis-conn job]
  (= 1 (redis-cmds/del-from-sorted-set redis-conn d/prefixed-schedule-queue job)))

(defn purge [redis-conn]
  (= 1 (redis-cmds/del-keys redis-conn d/prefixed-schedule-queue)))
