(ns goose.api.dead-jobs
  (:require
    [goose.api.init :as init]
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]))

(defn find-by-id
  [id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (r/find-in-sorted-set @init/broker-conn d/prefixed-dead-queue match? limit))))

(defn find-by-pattern
  ([match?]
   (find-by-pattern match? 10))
  ([match? limit]
   (r/find-in-sorted-set @init/broker-conn d/prefixed-dead-queue match? limit)))

(defn size
  []
  (r/sorted-set-size @init/broker-conn d/prefixed-dead-queue))

(defn enqueue-front-for-execution
  [job]
  (let [conn @init/broker-conn
        sorted-set d/prefixed-dead-queue]
    (when (r/sorted-set-score conn sorted-set job)
      (r/enqueue-due-jobs-to-front conn sorted-set (list job) scheduler/execution-queue))))

(defn delete
  [job]
  (= 1 (r/del-from-sorted-set @init/broker-conn d/prefixed-dead-queue job)))

(defn delete-older-than
  [date]
  (< 0 (r/del-from-sorted-set-until
         @init/broker-conn d/prefixed-dead-queue (u/epoch-time-ms date))))

(defn delete-all []
  (= 1 (r/del-keys @init/broker-conn [d/prefixed-dead-queue])))
