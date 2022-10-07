(ns goose.brokers.rmq.publisher-confirms
  (:refer-clojure :exclude [sync])
  (:require
    [goose.defaults :as d]

    [clojure.tools.logging :as log]))

(defn default-ack-handler
  [delivery-tag multiple]
  (if multiple
    (log/infof "ACK until delivery-tag: %d" delivery-tag)
    (log/infof "ACK for delivery-tag: %d" delivery-tag)))

(defn default-nack-handler
  [delivery-tag multiple]
  (if multiple
    (log/errorf "Negative-ACK until delivery-tag: %d" delivery-tag)
    (log/errorf "Negative-ACK for delivery-tag: %d" delivery-tag)))

(def sync
  {:strategy       d/sync-confirms
   :timeout-ms     1000
   :max-retries    3
   :retry-delay-ms 100})

(def async
  {:strategy     d/async-confirms
   :ack-handler  `default-ack-handler
   :nack-handler `default-nack-handler})
