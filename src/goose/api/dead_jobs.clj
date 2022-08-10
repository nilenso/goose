(ns goose.api.dead-jobs
  (:require
    [goose.brokers.broker :as b]))

(defn size
  "Get size of Dead Jobs."
  [broker-opts]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-size broker)))

(defn find-by-id
  "Find a Dead Job by ID."
  [broker-opts id]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-find-by-id broker id)))

(defn find-by-pattern
  "Find a Dead Job by pattern."
  ([broker-opts match?]
   (find-by-pattern broker-opts match? 10))
  ([broker-opts match? limit]
   (let [broker (b/new broker-opts)]
     (b/dead-jobs-find-by-pattern broker match? limit))))

(defn re-enqueue-for-execution
  "Move a job from dead-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-re-enqueue-for-execution broker job)))

(defn delete
  "Delete a Dead Job."
  [broker-opts job]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-delete broker job)))

(defn delete-older-than
  "Delete Dead Jobs older than a certain time.
  Note: The epoch should be in milliseconds."
  [broker-opts epoch-time-ms]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-delete-older-than broker epoch-time-ms)))

(defn delete-all
  "Delete all Dead Jobs."
  [broker-opts]
  (let [broker (b/new broker-opts)]
    (b/dead-jobs-delete-all broker)))
