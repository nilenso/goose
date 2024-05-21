(ns ^:no-doc goose.brokers.redis.console.data
  (:require [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.defaults :as d]
            [goose.job :as job]))

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

(defn- filter-jobs [redis-conn queue {:keys [filter-type filter-value limit]}]
  (case filter-type
    "id" [(enqueued-jobs/find-by-id redis-conn queue filter-value)]
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

(defn- paginated-jobs [redis-conn queue page]
  (let [start (* (- page 1) d/page-size)
        end (- (* page d/page-size) 1)]
    (enqueued-jobs/get-by-range redis-conn queue start end)))

(defn enqueued-page-data
  [redis-conn {:keys [page queue filter-type filter-value] :as params}]
  (let [queues (enqueued-jobs/list-all-queues redis-conn)
        queue (or queue (first queues))
        base-result {:queues queues
                     :page   page
                     :queue  queue}]
    (cond
      (and filter-type filter-value)
      (assoc base-result :jobs (filter-jobs redis-conn queue params))

      (nil? (or filter-type filter-value))
      (assoc base-result :jobs (paginated-jobs redis-conn queue page)
                         :total-jobs (enqueued-jobs/size redis-conn queue))

      :else base-result)))
