(ns goose.api.dead-jobs
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.brokers.redis.scheduler :as redis-scheduler]))

(defn size
  "Get size of Dead Jobs."
  [broker-opts]
  (let [conn (broker/new broker-opts)]
    (redis-cmds/sorted-set-size conn d/prefixed-dead-queue)))

(defn find-by-id
  "Find a Dead Job by ID."
  [broker-opts id]
  (let [conn (broker/new broker-opts)
        limit 1
        match? (fn [job] (= (:id job) id))]
    (first (redis-cmds/find-in-sorted-set conn d/prefixed-dead-queue match? limit))))

(defn find-by-pattern
  "Find a Dead Job by pattern."
  ([broker-opts match?]
   (find-by-pattern broker-opts match? 10))
  ([broker-opts match? limit]
   (let [conn (broker/new broker-opts)]
     (redis-cmds/find-in-sorted-set conn d/prefixed-dead-queue match? limit))))

(defn re-enqueue-for-execution
  "Move a job from dead-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        sorted-set d/prefixed-dead-queue]
    (when (redis-cmds/sorted-set-score conn sorted-set job)
      (redis-cmds/enqueue-due-jobs-to-front conn sorted-set (list job) redis-scheduler/execution-queue))))

(defn delete
  "Delete a Dead Job."
  [broker-opts job]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-from-sorted-set conn d/prefixed-dead-queue job))))

(defn delete-older-than
  "Delete Dead Jobs older than a certain time.
  Note: The epoch should be in milliseconds."
  [broker-opts epoch-time-ms]
  (let [conn (broker/new broker-opts)]
    (< 0 (redis-cmds/del-from-sorted-set-until
           conn d/prefixed-dead-queue epoch-time-ms))))

(defn delete-all
  "Delete all Dead Jobs."
  [broker-opts]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-keys conn [d/prefixed-dead-queue]))))
