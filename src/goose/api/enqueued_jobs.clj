(ns goose.api.enqueued-jobs
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]))

(defn list-all-queues [broker-opts]
  (let [conn (broker/new broker-opts)]
    (map d/affix-queue (redis-cmds/find-lists conn (str d/queue-prefix "*")))))

(defn size
  [broker-opts queue]
  (let [conn (broker/new broker-opts)]
    (redis-cmds/list-size conn (d/prefix-queue queue))))

(defn find-by-id
  [broker-opts queue id]
  (let [conn (broker/new broker-opts)
        limit 1
        match? (fn [job] (= (:id job) id))]
    (first (redis-cmds/find-in-list conn (d/prefix-queue queue) match? limit))))

(defn find-by-pattern
  ([broker-opts queue match?]
   (find-by-pattern broker-opts queue match? 10))
  ([broker-opts queue match? limit]
   (let [conn (broker/new broker-opts)]
     (redis-cmds/find-in-list conn (d/prefix-queue queue) match? limit))))

(defn prioritise-execution
  "Move a job after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        prefixed-queue (:prefixed-queue job)]
    (when (redis-cmds/list-position conn prefixed-queue job)
      (redis-cmds/del-from-list-and-enqueue-front conn prefixed-queue job))))

(defn delete
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        prefixed-queue (:prefixed-queue job)]
    (= 1 (redis-cmds/del-from-list conn prefixed-queue job))))

(defn delete-all
  [broker-opts queue]
  (let [conn (broker/new broker-opts)
        prefixed-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys conn [prefixed-queue]))))
