(ns goose.brokers.redis.console.data
  (:require [clojure.spec.alpha :as s]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.defaults :as d]
            [goose.brokers.redis.console.specs :as specs]))

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

(defn enqueued-page-data
  [redis-conn queue page]
  (let [page (if (s/valid? ::specs/page (specs/str->long page))
               (specs/str->long page)
               d/page)
        start (* (- page 1) d/page-size)
        end (- (* page d/page-size) 1)

        queues (enqueued-jobs/list-all-queues redis-conn)
        queue (or queue (first queues))
        total-jobs (enqueued-jobs/size redis-conn queue)
        jobs (enqueued-jobs/get-by-range redis-conn queue start end)]
    {:queues     queues
     :page       page
     :queue      queue
     :jobs       jobs
     :total-jobs total-jobs}))
