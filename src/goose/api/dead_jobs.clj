(ns goose.api.dead-jobs
  "API to manage dead jobs.
  - [API wiki](https://github.com/nilenso/goose/wiki/API)"
  (:refer-clojure :exclude [pop])
  (:require
   [goose.broker :as b]))

(defn size
  "Returns count of Dead Jobs."
  [broker]
  (b/dead-jobs-size broker))

(defn pop
  "Pops the oldest Dead Job from the queue & returns it."
  [broker]
  (b/dead-jobs-pop broker))

(defn find-by-id
  "Finds a Dead Job by `:id`."
  [broker id]
  (b/dead-jobs-find-by-id broker id))

(defn find-by-pattern
  "Finds a Dead Jobs by user-defined parameters.\\
  If limit isn't mentioned, defaults to 10."
  ([broker match?]
   (find-by-pattern broker match? 10))
  ([broker match? limit]
   (b/dead-jobs-find-by-pattern broker match? limit)))

(defn replay-job
  "Re-enqueues given Dead Job to front of queue for execution,
   after verification of existence.\\
  Hence, this accepts only 1 job instead of multiple."
  [broker job]
  (b/dead-jobs-replay-job broker job))

(defn replay-n-jobs
  "Re-enqueues n oldest Dead Jobs to front of queue for execution."
  [broker n]
  (b/dead-jobs-replay-n-jobs broker n))

(defn delete
  "Deletes given Dead Job."
  [broker job]
  (b/dead-jobs-delete broker job))

(defn delete-older-than
  "Deletes Dead Jobs older than given epoch-ms."
  [broker epoch-ms]
  (b/dead-jobs-delete-older-than broker epoch-ms))

(defn purge
  "Purges all the Dead Jobs."
  [broker]
  (b/dead-jobs-purge broker))
