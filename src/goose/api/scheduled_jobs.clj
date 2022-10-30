(ns goose.api.scheduled-jobs
  "API to manage scheduled jobs.\\
  - [API wiki](https://github.com/nilenso/goose/wiki/API)"
  (:require
    [goose.broker :as b]))

(defn size
  "Returns count of Scheduled Jobs."
  [broker]
  (b/scheduled-jobs-size broker))

(defn find-by-id
  "Finds a Scheduled Job by `:id`."
  [broker id]
  (b/scheduled-jobs-find-by-id broker id))

(defn find-by-pattern
  "Finds a Scheduled Jobs by user-defined parameters.\\
  If limit isn't mentioned, defaults to 10."
  ([broker match?]
   (find-by-pattern broker match? 10))
  ([broker match? limit]
   (b/scheduled-jobs-find-by-pattern broker match? limit)))

(defn prioritise-execution
  "Enqueues a Job scheduled to run at anytime to front of queue,
   after verification of existence.\\
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/scheduled-jobs-prioritise-execution broker job))

(defn delete
  "Deletes given Scheduled Job."
  [broker job]
  (b/scheduled-jobs-delete broker job))

(defn purge
  "Purges all the Scheduled Jobs."
  [broker]
  (b/scheduled-jobs-purge broker))
