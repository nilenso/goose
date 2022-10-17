(ns goose.api.scheduled-jobs
  (:require
    [goose.broker :as b]))

(defn size
  "Get size of Scheduled Jobs."
  [broker]
  (b/scheduled-jobs-size broker))

(defn find-by-id
  "Find a Scheduled Job by ID."
  [broker id]
  (b/scheduled-jobs-find-by-id broker id))

(defn find-by-pattern
  "Find a Scheduled Job by pattern.
  If limit isn't mentioned, defaults to 10."
  ([broker match?]
   (find-by-pattern broker match? 10))
  ([broker match? limit]
   (b/scheduled-jobs-find-by-pattern broker match? limit)))

(defn prioritise-execution
  "Move a job after scheduled-jobs to it's queue
  after verification of existence.
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/scheduled-jobs-prioritise-execution broker job))

(defn delete
  "Delete a Scheduled Job."
  [broker job]
  (b/scheduled-jobs-delete broker job))

(defn purge
  "Delete all Scheduled Jobs."
  [broker]
  (b/scheduled-jobs-purge broker))
