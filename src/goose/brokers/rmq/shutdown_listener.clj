(ns goose.brokers.rmq.shutdown-listener
  (:require [clojure.tools.logging :as log]))

(defn default
  [cause]
  (when-not (.isInitiatedByApplication cause)
    (log/error cause "RMQ connection shut down due to error")))
