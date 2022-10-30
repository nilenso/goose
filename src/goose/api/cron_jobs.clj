(ns goose.api.cron-jobs
  "API to manage cron-jobs AKA periodic jobs.
  To update a cron entry, call goose.client/perform-every
  with the cron entry name."
  (:require
    [goose.broker :as b]))

(defn size
  "Total count of all Periodic Jobs."
  [broker]
  (b/cron-jobs-size broker))

(defn find-by-name
  "Look up a cron entry by name."
  [broker entry-name]
  (b/cron-jobs-find-by-name broker entry-name))

(defn delete
  "Delete a cron entry."
  [broker entry-name]
  (b/cron-jobs-delete broker entry-name))

(defn purge
  "Purges all cron entries."
  [broker]
  (b/cron-jobs-purge broker))
