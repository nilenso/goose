(ns goose.api.dead-jobs
  (:refer-clojure :exclude [pop])
  (:require
    [goose.broker :as b]))

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
  "Find a Dead Job by pattern.
  If limit isn't mentioned, defaults to 10."
  ([broker match?]
   (find-by-pattern broker match? 10))
  ([broker match? limit]
   (b/dead-jobs-find-by-pattern broker match? limit)))

(defn replay-job
  "Move a job from dead-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/dead-jobs-replay-job broker job))

(defn replay-n-jobs
  "Replay n jobs from dead queue by moving them to ready queue"
  [broker n]
  (b/dead-jobs-replay-n-jobs broker n))

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
