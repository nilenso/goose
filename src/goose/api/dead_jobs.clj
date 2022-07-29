(ns goose.api.dead-jobs
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.scheduler :as scheduler]))

(defn size [broker-opts]
  (let [conn (broker/new broker-opts)]
    (redis-cmds/sorted-set-size conn d/prefixed-dead-queue)))

(defn find-by-id
  [broker-opts id]
  (let [conn (broker/new broker-opts)
        limit 1
        match? (fn [job] (= (:id job) id))]
    (first (redis-cmds/find-in-sorted-set conn d/prefixed-dead-queue match? limit))))

(defn find-by-pattern
  ([broker-opts match?]
   (find-by-pattern broker-opts match? 10))
  ([broker-opts match? limit]
   (let [conn (broker/new broker-opts)]
     (redis-cmds/find-in-sorted-set conn d/prefixed-dead-queue match? limit))))

(defn re-enqueue-for-execution
  "Move a job after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        sorted-set d/prefixed-dead-queue]
    (when (redis-cmds/sorted-set-score conn sorted-set job)
      (redis-cmds/enqueue-due-jobs-to-front conn sorted-set (list job) scheduler/execution-queue))))

(defn delete
  [broker-opts job]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-from-sorted-set conn d/prefixed-dead-queue job))))

(defn delete-older-than
  [broker-opts epoch-time-ms]
  (let [conn (broker/new broker-opts)]
    (< 0 (redis-cmds/del-from-sorted-set-until
           conn d/prefixed-dead-queue epoch-time-ms))))

(defn delete-all [broker-opts]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-keys conn [d/prefixed-dead-queue]))))
