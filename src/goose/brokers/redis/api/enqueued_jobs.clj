(ns ^:no-doc goose.brokers.redis.api.enqueued-jobs
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as job]))

(defn list-all-queues
  "Lists all the queues"
  [redis-conn]
  (map d/affix-queue (redis-cmds/find-lists redis-conn (str d/queue-prefix "*"))))

(defn size
  "Returns count of jobs in the queue"
  [redis-conn queue]
  (redis-cmds/list-size redis-conn (d/prefix-queue queue)))

(defn find-by-pattern
  "Finds job/s by user-defined parameters in given queue within the given limit"
  [redis-conn queue match? limit]
  (redis-cmds/find-in-list redis-conn (d/prefix-queue queue) match? limit))

(defn find-by-id
  "Finds a job by `:id` in given queue"
  [redis-conn queue id]
  (let [limit 1
        match? (fn [job] (= (:id job) id))]
    (first (find-by-pattern redis-conn queue match? limit))))

(defn prioritise-execution
  "Moves job/s to front of the queue after verification of existence"
  ([redis-conn {:keys [ready-queue] :as job}]
   (when (redis-cmds/list-position redis-conn ready-queue job)
     (redis-cmds/del-from-list-and-enqueue-front redis-conn ready-queue job)))

  ([redis-conn queue jobs]
   (let [remove-jobs-with-invalid-positions-fn (fn [p j] (->> (mapv vector p j)
                                                              (remove #(nil? (first %)))
                                                              (mapv second)))
         positions (redis-cmds/list-position-multiple redis-conn (d/prefix-queue queue) jobs)
         jobs (remove-jobs-with-invalid-positions-fn positions jobs)]
     (redis-cmds/del-from-list-and-enqueue-front-multiple redis-conn (d/prefix-queue queue) jobs))))

(defn delete
  "Delete job/s from its queue"
  ([redis-conn job]
   (let [queue (job/ready-or-retry-queue job)]
     (= 1 (redis-cmds/del-from-list redis-conn queue job))))

  ([redis-conn queue jobs]
   (not-any? #{0} (redis-cmds/del-from-list-multiple redis-conn (d/prefix-queue queue) jobs))))

(defn purge
  "Purges all the jobs in the queue"
  [redis-conn queue]
  (let [ready-queue (d/prefix-queue queue)]
    (= 1 (redis-cmds/del-keys redis-conn ready-queue))))

(defn get-by-range
  "Get all the jobs from start of queue to stop (inclusive)"
  [redis-conn queue start stop]
  (redis-cmds/range-from-front redis-conn (d/prefix-queue queue) start stop))
