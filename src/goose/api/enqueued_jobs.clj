(ns goose.api.enqueued-jobs
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]))

(defn list-all-queues
  "List all Queues."
  [broker-opts]
  (let [conn (broker/new broker-opts)]
    (map d/affix-queue (redis-cmds/find-lists conn (str d/queue-prefix "*")))))

(defn size
  "Get size of Enqueued Jobs in a Queue."
  [broker-opts queue]
  (let [conn (broker/new broker-opts)]
    (redis-cmds/list-size conn (d/prefix-queue queue))))

(defn find-by-id
  "Find an Enqueued Job by ID."
  [broker-opts queue id]
  (let [conn (broker/new broker-opts)
        limit 1
        match? (fn [job] (= (:id job) id))]
    (first (redis-cmds/find-in-list conn (d/prefix-queue queue) match? limit))))

(defn find-by-pattern
  "Find an Enqueued Job by pattern."
  ([broker-opts queue match?]
   (find-by-pattern broker-opts queue match? 10))
  ([broker-opts queue match? limit]
   (let [conn (broker/new broker-opts)]
     (redis-cmds/find-in-list conn (d/prefix-queue queue) match? limit))))

(defn prioritise-execution
  "Move a job to front of it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        prefixed-queue (:prefixed-queue job)]
    (when (redis-cmds/list-position conn prefixed-queue job)
      (redis-cmds/del-from-list-and-enqueue-front conn prefixed-queue job))))

(defn delete
  "Delete an Enqueued Job."
  [broker-opts job]
  (let [conn (broker/new broker-opts)
        prefixed-queue (:prefixed-queue job)]
    (= 1 (redis-cmds/del-from-list conn prefixed-queue job))))

(defn delete-all
  "Delete entire Queue containing Enqueued Jobs."
  [broker-opts queue]
  (let [conn (broker/new broker-opts)
        prefixed-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys conn [prefixed-queue]))))
