(ns goose.validations.queue
  (:require
    [goose.config :as cfg]
    [goose.utils :as u]
    [clojure.string :as str]))


(defn- queue-invalid?
  "Valid queue: An unprefixed string.
  Max length: 1000 characters"
  [queue]
  (or
    (not (string? queue))
    (str/starts-with? queue cfg/queue-prefix)
    (< 1000 (count queue))))

(defn validate-queue
  [queue]
  (if (queue-invalid? queue)
    (throw (ex-info "Invalid queue" (u/wrap-error :queues-invalid queue)))))
