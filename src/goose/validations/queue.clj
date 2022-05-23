(ns goose.validations.queue
  (:require
    [goose.config :as cfg]
    [clojure.string :as str]))


(defn queue-invalid?
  "Valid queue: An unprefixed string.
  Max length: 1000 characters"
  [queue]
  (or
    (not (string? queue))
    (str/starts-with? queue cfg/queue-prefix)
    (< 1000 (count queue))))

(defn queues-invalid?
  "List/Vector of valid queues."
  [queues]
  (reduce
    (fn [prev q] (or prev (queue-invalid? q)))
    false
    queues))
