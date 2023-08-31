(ns goose.api.cron-jobs
  "API to manage Periodic Jobs AKA Cron Entries.\\
  To update a cron entry, call [[goose.client/perform-every]] since it is idempotent.
  - [API wiki](https://github.com/nilenso/goose/wiki/API)"
  (:require
    [goose.broker :as b]))

(defn size
  "Returns count of Periodic Jobs."
  [broker]
  (b/cron-jobs-size broker))

(defn find-by-name
  "Finds a Cron Entry by `:name`."
  [broker entry-name]
  (b/cron-jobs-find-by-name broker entry-name))

(defn delete
  "Deletes Cron Entry & Cron-Scheduled Job of given `:name`."
  [broker entry-name]
  (b/cron-jobs-delete broker entry-name))

(defn purge
  "Purges all the Cron Entries & Cron-Scheduled Jobs."
  [broker]
  (b/cron-jobs-purge broker))
