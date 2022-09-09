(ns goose.api.dead-jobs
  (:refer-clojure :exclude [pop])
  (:require
    [goose.brokers.broker :as b]))

(defn size
  "Get size of Dead Jobs."
  [broker]
  (b/dead-jobs-size broker))

(defn pop
  "Return Job at head of Dead-queue."
  [broker]
  (b/dead-jobs-pop broker))

(defn find-by-id
  "Find a Dead Job by ID."
  [broker id]
  (b/dead-jobs-find-by-id broker id))

(defn find-by-pattern
  "Find a Dead Job by pattern."
  ([broker match?]
   ;; TODO: Either document this magic number or get rid of it
   ;; When skipping the limit parameter, the intuitive assumption
   ;; is that there is no limit, not that a default limit is used
   (find-by-pattern broker match? 10))
  ([broker match? limit]
   (b/dead-jobs-find-by-pattern broker match? limit)))

(defn re-enqueue-for-execution
  "Move a job from dead-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/dead-jobs-re-enqueue-for-execution broker job))

(defn delete
  "Delete a Dead Job."
  [broker job]
  (b/dead-jobs-delete broker job))

(defn delete-older-than
  "Delete Dead Jobs older than a certain time.
  Note: The epoch should be in milliseconds."
  [broker epoch-time-ms]
  (b/dead-jobs-delete-older-than broker epoch-time-ms))

(defn purge
  "Delete all Dead Jobs."
  [broker]
  (b/dead-jobs-purge broker))
