(ns goose.api.dead-jobs
  (:require
    [goose.api.api :as api]
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.scheduler :as scheduler]))

(defn find-by-id
  [id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (r/find-in-sorted-set @api/broker-conn d/prefixed-dead-queue match? limit))))

(defn find-by-pattern
  ([match?]
   (find-by-pattern match? 10))
  ([match? limit]
   (r/find-in-sorted-set @api/broker-conn d/prefixed-dead-queue match? limit)))

(defn size []
  (r/sorted-set-size @api/broker-conn d/prefixed-dead-queue))

(defn re-enqueue-for-execution
  "Move a job after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [job]
  (let [conn @api/broker-conn
        sorted-set d/prefixed-dead-queue]
    (when (r/sorted-set-score conn sorted-set job)
      (r/enqueue-due-jobs-to-front conn sorted-set (list job) scheduler/execution-queue))))

(defn delete
  [job]
  (= 1 (r/del-from-sorted-set @api/broker-conn d/prefixed-dead-queue job)))

(defn delete-older-than
  [epoch-time-ms]
  (< 0 (r/del-from-sorted-set-until
         @api/broker-conn d/prefixed-dead-queue epoch-time-ms)))

(defn delete-all []
  (= 1 (r/del-keys @api/broker-conn [d/prefixed-dead-queue])))
