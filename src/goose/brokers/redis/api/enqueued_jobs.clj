(ns ^:no-doc goose.brokers.redis.api.enqueued-jobs
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]))

(defn list-all-queues [redis-conn]
  (map d/affix-queue (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))))

(defn size [redis-conn queue]
  (redis-cmds/list-size redis-conn (d/prefix-queue queue)))

(defn find-by-pattern [redis-conn queue match? limit]
  (redis-cmds/find-in-list redis-conn (d/prefix-queue queue) match? limit))

(defn find-by-id [redis-conn queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern redis-conn queue match? limit))))

(defn prioritise-execution [redis-conn job]
  (let [ready-queue (:ready-queue job)]
    (when (redis-cmds/list-position redis-conn ready-queue job)
      (redis-cmds/del-from-list-and-enqueue-front redis-conn ready-queue job))))

(defn delete
  ([redis-conn job]
   (delete redis-conn job (:ready-queue job)))
  ([redis-conn job queue]
   (= 1 (redis-cmds/del-from-list redis-conn queue job))))

(defn purge [redis-conn queue]
  (let [ready-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys redis-conn ready-queue))))
