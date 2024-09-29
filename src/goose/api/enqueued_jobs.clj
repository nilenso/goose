(ns goose.api.enqueued-jobs
  "API to manage enqueued jobs.
  - [API wiki](https://github.com/nilenso/goose/wiki/API)"
  (:require
   [goose.broker :as b]))

(defn list-all-queues
  "Lists all the queues."
  [broker]
  (b/enqueued-jobs-list-all-queues broker))

(defn size
  "Returns count of Jobs in given queue."
  [broker queue]
  (b/enqueued-jobs-size broker queue))

(defn find-by-id
  "Finds a Job by `:id` in given queue."
  [broker queue id]
  (b/enqueued-jobs-find-by-id broker queue id))

(defn find-by-pattern
  "Finds a Job by user-defined parameters in given queue.\\
  If limit isn't mentioned, defaults to 10."
  ([broker queue match?]
   (find-by-pattern broker queue match? 10))
  ([broker queue match? limit]
   (b/enqueued-jobs-find-by-pattern broker queue match? limit)))

(defn prioritise-execution
  "Brings a Job anywhere in the queue to front of queue,
   after verification of existence.\\
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/enqueued-jobs-prioritise-execution broker job))

(defn delete
  "Deletes given Job from its queue."
  [broker job]
  (b/enqueued-jobs-delete broker job))

(defn purge
  "Purges all the Jobs in given queue."
  [broker queue]
  (b/enqueued-jobs-purge broker queue))
