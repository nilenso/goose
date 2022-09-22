(ns goose.brokers.rmq.consumer
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]
    [langohr.consumers :as lc]))

(defn execute-job
  [{:keys                  [ch]
    {:keys [delivery-tag]} :metadata}
   {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args)
  (lb/ack ch delivery-tag))

(defn- handler
  [{:keys [call thread-pool] :as opts}
   ch
   metadata
   ^bytes payload]
  (let [job (u/decode payload)
        ; Attach RMQ message metadata for ACKing & middlewares.
        ; https://www.rabbitmq.com/publishers.html#message-properties
        opts (assoc opts :ch ch :metadata metadata)]
    (cp/future thread-pool (call opts job))))

(defn run
  [{:keys [channels ready-queue] :as opts}]
  (doall ; Using `doall` to immediately start a consumer.
    (for [ch channels]
      (do
        (lb/qos ch d/rmq-prefetch-limit) ; Set prefetch-limit to 1.
        [ch (lc/subscribe ch ready-queue (partial handler opts) {:auto-ack false})]))))
