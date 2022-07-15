(ns goose.api.enqueued-jobs
  (:require
    [goose.api.init :as init]
    [goose.defaults :as d]
    [goose.redis :as r]))

(defn list-all-queues []
  (map d/affix-queue (r/list-queues @init/broker-conn)))

(defn find-by-id
  [queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (r/find-in-list @init/broker-conn (d/prefix-queue queue) match? limit))))

(defn find-by-pattern
  ([queue match?]
   (find-by-pattern queue match? 10))
  ([queue match? limit]
   (r/find-in-list @init/broker-conn (d/prefix-queue queue) match? limit)))

(defn size
  [queue]
  (r/list-size @init/broker-conn (d/prefix-queue queue)))

(defn enqueue-front-for-execution
  [queue job]
  (let [prefixed-queue (d/prefix-queue queue)
        conn @init/broker-conn]
    (when (r/list-position conn prefixed-queue job)
      (r/del-from-list-and-enqueue-front conn (d/prefix-queue queue) job))))

(defn delete
  [queue job]
  (= 1 (r/del-from-list @init/broker-conn (d/prefix-queue queue) job)))

(defn delete-all
  [queue]
  (= 1 (r/del-keys @init/broker-conn [(d/prefix-queue queue)])))

(find-by-id "default" "4382be97-1843-41a1-8226-84d0fe45e4f5")
(count (find-by-pattern "default" (fn [_] true)))
(list-all-queues)
