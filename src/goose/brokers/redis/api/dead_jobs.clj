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

(defn replay-job [redis-conn job]
  (let [sorted-set d/prefixed-dead-queue]
    (when (redis-cmds/sorted-set-score redis-conn sorted-set job)
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set (list job) job/ready-or-retry-queue))))

(defn replay-n-jobs [redis-conn n]
  (let [sorted-set d/prefixed-dead-queue
        jobs (redis-cmds/sorted-set-peek-jobs redis-conn sorted-set n)]
    (when (< 0 (count jobs))
      (redis-cmds/sorted-set->ready-queue redis-conn sorted-set jobs job/ready-or-retry-queue))
    (count jobs)))

(defn delete [redis-conn job]
  (= 1 (redis-cmds/del-from-sorted-set redis-conn d/prefixed-dead-queue job)))

(defn delete-older-than [redis-conn epoch-ms]
  (< 0 (redis-cmds/del-from-sorted-set-until
         redis-conn d/prefixed-dead-queue epoch-ms)))

(defn purge [redis-conn]
  (= 1 (redis-cmds/del-keys redis-conn [d/prefixed-dead-queue])))
