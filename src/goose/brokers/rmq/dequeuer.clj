(ns goose.brokers.rmq.dequeuer
  {:no-doc true}
  (:require
    [taoensso.nippy :as nippy]
    [langohr.consumers :as lc]
    [langohr.basic :as lb]
    [goose.utils :as u]))

(defn execute-job
  [{:keys [ch delivery-tag]} {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args)
  (lb/ack ch delivery-tag))

(defn- handler
  [{:keys [call] :as opts}]
  (fn [ch {:keys [delivery-tag]} ^bytes payload]
    (let [job (nippy/thaw payload)
          opts (assoc opts :ch ch :delivery-tag delivery-tag)]
      (call opts job))))

(defn run
  [{:keys [prefixed-queue ch] :as opts}]
  (u/log-on-exceptions
    (lc/subscribe ch prefixed-queue (handler opts) {:auto-ack false})))
