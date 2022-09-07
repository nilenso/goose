(ns goose.api.enqueued-jobs
  (:require
    [goose.brokers.broker :as b]))

(defn list-all-queues
  "List all Queues."
  [broker]
  (b/enqueued-jobs-list-all-queues broker))

(defn size
  "Get size of Enqueued Jobs in a Queue."
  [broker queue]
  (b/enqueued-jobs-size broker queue))

(defn find-by-id
  "Find an Enqueued Job by ID."
  [broker queue id]
  (b/enqueued-jobs-find-by-id broker queue id))

(defn find-by-pattern
  "Find an Enqueued Job by pattern."
  ([broker queue match?]
   (find-by-pattern broker queue match? 10))
  ([broker queue match? limit]
   (b/enqueued-jobs-find-by-pattern broker queue match? limit)))

(defn prioritise-execution
  "Move a job to front of it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/enqueued-jobs-prioritise-execution broker job))

(defn delete
  "Delete an Enqueued Job."
  [broker job]
  (b/enqueued-jobs-delete broker job))

(defn purge
  "Delete entire Queue containing Enqueued Jobs."
  [broker queue]
  (b/enqueued-jobs-purge broker queue))
