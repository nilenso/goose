(ns goose.brokers.rmq.dequeuer
  {:no-doc true}
  (:require
    [goose.utils :as u]

    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]
    [taoensso.nippy :as nippy]))

(defn execute-job
  [{:keys [ch delivery-tag]} {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args)
  (lb/ack ch delivery-tag))

(defn handler
  [{:keys [call thread-pool] :as opts}]
  (fn [ch {:keys [delivery-tag]} ^bytes payload]
    (let [job (nippy/thaw payload)
          opts (assoc opts :ch ch :delivery-tag delivery-tag)]
      (cp/future thread-pool (call opts job)))))
