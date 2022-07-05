(ns goose.brokers.broker
  (:require
    [goose.brokers.redis :as redis]))

(defn create
  ([opts]
   (create opts nil))
  ([opts thread-count]
   (cond
     (:redis opts)
     (redis/new (:redis opts) thread-count)

     :else (throw (ex-info "broker-opts missing" {})))))

