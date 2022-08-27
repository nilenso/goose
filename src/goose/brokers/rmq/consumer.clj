(ns goose.brokers.rmq.consumer
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]
    [langohr.consumers :as lc]
    [taoensso.nippy :as nippy]))

(defn execute-job
  [{:keys [ch delivery-tag]} {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args)
  (lb/ack ch delivery-tag))

(defn- handler
  [{:keys [call thread-pool] :as opts}
   ch
   {:keys [delivery-tag]}
   ^bytes payload]
  (let [job (nippy/thaw payload)
        opts (assoc opts :ch ch :delivery-tag delivery-tag)]
    (cp/future thread-pool (call opts job))))

(defn run
  [{:keys [channels prefixed-queue] :as opts}]
  (doall ; Using `doall` to immediately start a consumer.
    (for [ch channels]
      (let [opts (assoc opts :ch ch)]
        ; Set prefetch-limit to 1.
        (lb/qos ch d/rmq-prefetch-limit)
        [ch (lc/subscribe ch prefixed-queue (partial handler opts) {:auto-ack false})]))))
