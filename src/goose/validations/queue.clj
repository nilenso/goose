(ns goose.validations.queue
  (:require
    [goose.config :as cfg]))


(defn queue-invalid?
  "Valid queue: An unprefixed string.
  Max length: 1000 characters"
  [queue]
  (or
    (not (string? queue))
    (clojure.string/starts-with? queue cfg/queue-prefix)
    (< 1000 (count queue))))

(defn queues-invalid?
  "List/Vector of valid queues."
  [queues]
  (or
    (reduce
      (fn [prev q] (or prev (queue-invalid? q)))
      false
      queues)))
