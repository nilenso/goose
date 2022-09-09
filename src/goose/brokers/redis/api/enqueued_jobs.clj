(ns goose.brokers.redis.api.enqueued-jobs
  {:no-doc true}
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]))

(defn list-all-queues [conn]
  (map d/affix-queue (redis-cmds/find-lists conn (str d/queue-prefix "*"))))

(defn size [conn queue]
  (redis-cmds/list-size conn (d/prefix-queue queue)))

(defn find-by-pattern [conn queue match? limit]
  (redis-cmds/find-in-list conn (d/prefix-queue queue) match? limit))

(defn find-by-id [conn queue id]
  (let [
        ;; TODO: Think about this limit more carefully. Why is it hardcoded?
        ;; It used to be 1, which was breaking with the new scan implementation.
        limit 10
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern conn queue match? limit))))

(defn prioritise-execution [conn job]
  (let [prefixed-queue (:prefixed-queue job)]
    (when (redis-cmds/list-position conn prefixed-queue job)
      (redis-cmds/del-from-list-and-enqueue-front conn prefixed-queue job))))

(defn delete [conn job]
  (let [prefixed-queue (:prefixed-queue job)]
    (= 1 (redis-cmds/del-from-list conn prefixed-queue job))))

(defn purge [conn queue]
  (let [prefixed-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys conn [prefixed-queue]))))
