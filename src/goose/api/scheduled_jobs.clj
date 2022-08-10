(ns goose.api.scheduled-jobs
  (:require
    [goose.brokers.broker :as b]))

(defn size
  "Get size of Scheduled Jobs."
  [broker-opts]
  (let [broker (b/new broker-opts)]
    (b/scheduled-jobs-size broker)))

(defn find-by-id
  "Find a Scheduled Job by ID."
  [broker-opts id]
  (let [broker (b/new broker-opts)]
    (b/scheduled-jobs-find-by-id broker id)))

(defn find-by-pattern
  "Find a Scheduled Job by pattern."
  ([broker-opts match?]
   (find-by-pattern broker-opts match? 10))
  ([broker-opts match? limit]
   (let [broker (b/new broker-opts)]
     (b/scheduled-jobs-find-by-pattern broker match? limit))))

(defn prioritise-execution
  "Move a job after scheduled-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/scheduled-jobs-prioritise-execution broker job)))

(defn delete
  "Delete a Scheduled Job."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/scheduled-jobs-delete broker job)))

(defn delete-all
  "Delete all Scheduled Jobs."
  [broker-opts]
  (let [broker (b/new broker-opts)]
    (b/scheduled-jobs-delete-all broker)))
