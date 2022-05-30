(ns goose.validations.queue
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]
    [clojure.string :as str]))


(defn- queue-invalid?
  "Valid queue: An unprefixed string.
  Max length: 1000 characters"
  [queue]
  (or
    (not (string? queue))
    (str/starts-with? queue d/queue-prefix)
    (< 1000 (count queue))
    (clojure.string/includes? d/protected-queues queue)))

(defn validate-queue
  [queue]
  (when (and queue (queue-invalid? queue))
    (throw (ex-info "Invalid queue" (u/wrap-error :queues-invalid queue)))))
