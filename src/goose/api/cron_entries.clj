(ns goose.api.cron-entries
  "Functions for working with cron entries.
  To update a cron entry, call goose.client/perform-every
  with the cron entry name."
  (:require [goose.brokers.broker :as b]))

(defn find-by-name
  "Look up a cron entry by name."
  [broker entry-name]
  (b/cron-entries-find-by-name broker entry-name))

(defn delete
  "Delete a cron entry."
  [broker entry-name]
  (b/cron-entries-delete broker entry-name))

(defn delete-all
  "Delete all cron entries."
  [broker]
  (b/cron-entries-delete-all broker))
