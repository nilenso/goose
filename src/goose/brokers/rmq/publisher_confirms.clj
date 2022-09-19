(ns goose.brokers.rmq.publisher-confirms
  (:refer-clojure :exclude [sync])
  (:require
    [goose.defaults :as d]

    [clojure.tools.logging :as log]))

(defn default-ack-handler
  [delivery-tag multiple]
  (if multiple
    (log/info (format "ACK until delivery-tag: %d" delivery-tag))
    (log/info (format "ACK for delivery-tag: %d" delivery-tag))))

(defn default-nack-handler
  [delivery-tag multiple]
  (if multiple
    (log/error (format "Negative-ACK uptil delivery-tag: %d" delivery-tag))
    (log/error (format "Negative-ACK for delivery-tag: %d" delivery-tag))))

(def sync
  {:strategy   d/sync-confirms
   :timeout-ms 5000})

(def async
  {:strategy     d/async-confirms
   :ack-handler  `default-ack-handler
   :nack-handler `default-nack-handler})
