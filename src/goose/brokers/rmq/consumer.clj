(ns goose.brokers.rmq.consumer
  ^:no-doc
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]
    [langohr.basic :as lb]
    [langohr.consumers :as lc]))

(defn wrap-acks
  [next]
  (fn [{:keys                  [ch] :as opts
        {:keys [delivery-tag]} :metadata}
       job]
    (next opts job)
    (lb/ack ch delivery-tag)))

(defn- handler
  [{:keys [call] :as opts}
   ch
   metadata
   ^bytes payload]
  (let [job (u/decode payload)
        ;; Attach RMQ message metadata for ACKing & middlewares.
        ;; https://www.rabbitmq.com/publishers.html#message-properties
        opts (assoc opts :ch ch :metadata metadata)]
    (call opts job)))

(defn- consume-ok
  [queue consumer_tag]
  (log/debugf "consumer started for queue: %s, consumer_tag: %s" queue consumer_tag))

(defn- cancel-ok
  [queue consumer_tag]
  (log/debugf "consumer cancelled for queue: %s, consumer_tag: %s" queue consumer_tag))

(defn- recover-ok
  [queue]
  (log/debugf "consumer recovered for queue: %s" queue))

(defn- shutdown-signal
  [consumer_tag reason]
  (log/debugf "channel shutdown for consumer_tag: %s, reason: %s" consumer_tag reason))

(defn run
  [{:keys [channels ready-queue] :as opts}]
  (doall ; Using `doall` to immediately start a consumer.
    (for [ch channels]
      (do
        (lb/qos ch d/rmq-prefetch-limit) ; Set prefetch-limit to 1.
        (let [subscriber-opts {:auto-ack               false
                               :handle-cancel-ok       #(cancel-ok ready-queue %)
                               :handle-consume-ok      #(consume-ok ready-queue %)
                               :handle-recover-ok      #(recover-ok ready-queue)
                               :handle-shutdown-signal shutdown-signal}]
          [ch (lc/subscribe ch ready-queue #(handler opts %1 %2 %3) subscriber-opts)])))))
