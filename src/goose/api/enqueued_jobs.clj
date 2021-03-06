(ns goose.api.enqueued-jobs
  (:require
    [goose.api.api :as api]
    [goose.defaults :as d]
    [goose.redis :as r]))

(defn list-all-queues []
  (map d/affix-queue (r/find-lists @api/broker-conn (str d/queue-prefix "*"))))

(defn find-by-id
  [queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (r/find-in-list @api/broker-conn (d/prefix-queue queue) match? limit))))

(defn find-by-pattern
  ([queue match?]
   (find-by-pattern queue match? 10))
  ([queue match? limit]
   (r/find-in-list @api/broker-conn (d/prefix-queue queue) match? limit)))

(defn size
  [queue]
  (r/list-size @api/broker-conn (d/prefix-queue queue)))

(defn prioritise-execution
  "Move a job after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [queue job]
  (let [prefixed-queue (d/prefix-queue queue)
        conn @api/broker-conn]
    (when (r/list-position conn prefixed-queue job)
      (r/del-from-list-and-enqueue-front conn (d/prefix-queue queue) job))))

(defn delete
  [queue job]
  (= 1 (r/del-from-list @api/broker-conn (d/prefix-queue queue) job)))

(defn delete-all
  [queue]
  (= 1 (r/del-keys @api/broker-conn [(d/prefix-queue queue)])))
