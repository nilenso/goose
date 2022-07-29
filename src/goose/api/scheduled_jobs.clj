(ns goose.api.scheduled-jobs
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.scheduler :as scheduler]))

(defn size
  "Get size of Scheduled Jobs."
  [broker-opts]
  (let [conn (broker/new broker-opts)]
    (redis-cmds/sorted-set-size conn d/prefixed-schedule-queue)))

(defn find-by-id
  "Find a Scheduled Job by ID."
  [broker-opts id]
  (let [conn (broker/new broker-opts)
        limit 1
        match? (fn [job] (= (:id job) id))]
    (first (redis-cmds/find-in-sorted-set conn d/prefixed-schedule-queue match? limit))))

(defn find-by-pattern
  "Find a Scheduled Job by pattern."
  ([broker-opts match?]
   (find-by-pattern broker-opts match? 10))
  ([broker-opts match? limit]
   (let [conn (broker/new broker-opts)]
     (redis-cmds/find-in-sorted-set conn d/prefixed-schedule-queue match? limit))))

(defn prioritise-execution
  "Move a job after scheduled-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        sorted-set d/prefixed-schedule-queue]
    (when (redis-cmds/sorted-set-score conn sorted-set job)
      (redis-cmds/enqueue-due-jobs-to-front conn sorted-set (list job) scheduler/execution-queue))))

(defn delete
  "Delete a Scheduled Job."
  [broker-opts job]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-from-sorted-set conn d/prefixed-schedule-queue job))))

(defn delete-all
  "Delete all Scheduled Jobs."
  [broker-opts]
  (let [conn (broker/new broker-opts)]
    (= 1 (redis-cmds/del-keys conn [d/prefixed-schedule-queue]))))
