(ns ^:no-doc goose.brokers.redis.console.data
  (:require [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.defaults :as d]
            [goose.job :as job]))

(defn- invalid-filter-value? [filter-value]
  (nil? filter-value))

(defn- filter-jobs-request? [filter-type filter-value]
  (every? (comp not nil?) [filter-type filter-value]))

(defn- get-all-jobs-request? [filter-type filter-value]
  (every? nil? [filter-type filter-value]))

(defn jobs-size [redis-conn]
  (let [queues (enqueued-jobs/list-all-queues redis-conn)
        enqueued (reduce (fn [total queue]
                           (+ total (enqueued-jobs/size redis-conn queue))) 0 queues)
        scheduled (scheduled-jobs/size redis-conn)
        periodic (periodic-jobs/size redis-conn)
        dead (dead-jobs/size redis-conn)]
    {:enqueued  enqueued
     :scheduled scheduled
     :periodic  periodic
     :dead      dead}))

(defn- filter-enqueued-jobs [redis-conn queue {:keys [filter-type filter-value limit]}]
  (case filter-type
    "id" (if-let [job (enqueued-jobs/find-by-id redis-conn queue filter-value)] [job] [])
    "execute-fn-sym" (enqueued-jobs/find-by-pattern redis-conn
                                                    queue
                                                    (fn [j]
                                                      (= (:execute-fn-sym j)
                                                         (symbol filter-value)))
                                                    limit)
    "type" (case filter-value
             "failed"
             (enqueued-jobs/find-by-pattern redis-conn queue job/retried? limit)

             "unexecuted"
             (enqueued-jobs/find-by-pattern redis-conn
                                            queue (comp not job/retried?) limit)

             nil)
    nil))

(defn enqueued-page-data
  [redis-conn {:keys                  [page queue]
               validated-filter-type  :filter-type
               validated-filter-value :filter-value
               :as                    params}]
  (let [queues (enqueued-jobs/list-all-queues redis-conn)
        queue (or queue (first queues))
        base-result {:queues queues
                     :page   page
                     :queue  queue}
        no-jobs-response (assoc base-result :jobs [])]
    (cond
      (empty? queues) no-jobs-response

      (filter-jobs-request? validated-filter-type validated-filter-value)
      (assoc base-result :jobs (filter-enqueued-jobs redis-conn queue params))

      (get-all-jobs-request? validated-filter-type validated-filter-value)
      (assoc base-result :jobs (enqueued-jobs/get-by-range redis-conn
                                                           queue
                                                           (* (dec page) d/page-size)
                                                           (dec (* page d/page-size)))
                         :total-jobs (enqueued-jobs/size redis-conn queue))

      (invalid-filter-value? validated-filter-value) no-jobs-response)))

(defn- filter-dead-jobs [redis-conn {:keys [filter-type filter-value limit]}]
  (case filter-type
    "id" (if-let [job (dead-jobs/find-by-id redis-conn filter-value)] [job] [])
    "execute-fn-sym" (dead-jobs/find-by-pattern redis-conn
                                                (fn [j]
                                                  (= (:execute-fn-sym j)
                                                     (symbol filter-value)))
                                                limit)
    "queue" (dead-jobs/find-by-pattern redis-conn
                                       (fn [j]
                                         (= (:queue j) filter-value))
                                       limit)
    nil))

(defn dead-page-data
  [redis-conn {:keys                  [page]
               validated-filter-type  :filter-type
               validated-filter-value :filter-value
               :as                    params}]
  (let [base-result {:page page}]
    (cond
      (filter-jobs-request? validated-filter-type validated-filter-value)
      (assoc base-result :jobs (filter-dead-jobs redis-conn params))

      (get-all-jobs-request? validated-filter-type validated-filter-value)
      (assoc base-result :jobs (dead-jobs/get-by-range redis-conn
                                                       (* d/page-size (dec page))
                                                       (dec (* d/page-size page)))
                         :total-jobs (dead-jobs/size redis-conn))

      (invalid-filter-value? validated-filter-value)
      (assoc base-result :jobs []))))
