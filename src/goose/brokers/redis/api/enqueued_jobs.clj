(ns ^:no-doc goose.brokers.redis.api.enqueued-jobs
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]))

(defn list-all-queues [redis-conn]
  (map d/affix-queue (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))))

(defn size [redis-conn queue]
  (redis-cmds/list-size redis-conn (d/prefix-queue queue)))

(defn find-by-pattern [redis-conn queue match? limit]
  (redis-cmds/find-in-list redis-conn (d/prefix-queue queue) match? limit))

(defn find-by-id [redis-conn queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern redis-conn queue match? limit))))

(defn prioritise-execution
  ([redis-conn {:keys [ready-queue] :as job}]
   (when (redis-cmds/list-position redis-conn ready-queue job)
     (redis-cmds/del-from-list-and-enqueue-front redis-conn ready-queue job)))

  ;; This isn't exposed by Broker API and is used internally by console
  ([redis-conn queue jobs]
   (let [remove-jobs-with-invalid-positions-fn (fn [p j] (->> (mapv vector p j)
                                                              (remove #(nil? (first %)))
                                                              (mapv second)))
         positions (redis-cmds/list-position-multiple redis-conn (d/prefix-queue queue) jobs)
         jobs (remove-jobs-with-invalid-positions-fn positions jobs)]
     (redis-cmds/del-from-list-and-enqueue-front-multiple redis-conn (d/prefix-queue queue) jobs))))

(defn delete
  ([redis-conn job]
   (let [queue (job/ready-or-retry-queue job)]
     (= 1 (redis-cmds/del-from-list redis-conn queue job))))

  ;; Used internally by console
  ([redis-conn queue jobs]
   (not-any? #{0} (redis-cmds/del-from-list-multiple redis-conn (d/prefix-queue queue) jobs))))

(defn purge [redis-conn queue]
  (let [ready-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys redis-conn ready-queue))))

;;Used internally by console
(defn get-by-range [redis-conn queue start stop]
  (redis-cmds/range-from-front redis-conn (d/prefix-queue queue) start stop))
