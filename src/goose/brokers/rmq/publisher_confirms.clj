(ns goose.brokers.rmq.publisher-confirms
  (:refer-clojure :exclude [sync])
  (:require
    [goose.defaults :as d]

    [clojure.tools.logging :as log]))

(defn default-ack-handler
  [ch-number delivery-tag multiple]
  (if multiple
    (log/infof "[ch: %d] ACK until delivery-tag: %d" ch-number delivery-tag)
    (log/infof "[ch: %d] ACK for delivery-tag: %d" ch-number delivery-tag)))

(defn default-nack-handler
  [channel-number delivery-tag multiple]
  (if multiple
    (log/errorf "[ch: %d] Negative-ACK until delivery-tag: %d" channel-number delivery-tag)
    (log/errorf "[ch: %d] Negative-ACK for delivery-tag: %d" channel-number delivery-tag)))

(def sync
  {:strategy       d/sync-confirms
   :timeout-ms     1000
   :max-retries    3
   :retry-delay-ms 100})

(def async
  {:strategy     d/async-confirms
   :ack-handler  default-ack-handler
   :nack-handler default-nack-handler})
