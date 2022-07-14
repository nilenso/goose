(ns goose.api.enqueued-jobs
  (:require
    [goose.api.init :as init]
    [goose.defaults :as d]
    [goose.redis :as r]))

(defn find-by-id
  [queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (r/find-in-queue @init/broker-conn (d/prefix-queue queue) match? limit))))

(defn find-by-pattern
  ([queue match?]
   (find-by-pattern queue match? 10))
  ([queue match? limit]
   (r/find-in-queue @init/broker-conn (d/prefix-queue queue) match? limit)))

(defn delete
  [{:keys [queue] :as job}]
  (= 1 (r/remove-from-list @init/broker-conn queue job)))
