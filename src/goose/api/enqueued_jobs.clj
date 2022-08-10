(ns goose.api.enqueued-jobs
  (:require
    [goose.brokers.broker :as b]))

(defn list-all-queues
  "List all Queues."
  [broker-opts]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-list-all-queues broker)))

(defn size
  "Get size of Enqueued Jobs in a Queue."
  [broker-opts queue]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-size broker queue)))

(defn find-by-id
  "Find an Enqueued Job by ID."
  [broker-opts queue id]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-find-by-id broker queue id)))

(defn find-by-pattern
  "Find an Enqueued Job by pattern."
  ([broker-opts queue match?]
   (find-by-pattern broker-opts queue match? 10))
  ([broker-opts queue match? limit]
   (let [broker (b/new broker-opts)]
     (b/enqueued-jobs-find-by-pattern broker queue match? limit))))

(defn prioritise-execution
  "Move a job to front of it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-prioritise-execution broker job)))

(defn delete
  "Delete an Enqueued Job."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-delete broker job)))

(defn delete-all
  "Delete entire Queue containing Enqueued Jobs."
  [broker-opts queue]
  (let [broker (b/new broker-opts)]
    (b/enqueued-jobs-delete-all broker queue)))
