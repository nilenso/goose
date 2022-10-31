(ns goose.brokers.rmq.shutdown-listener
  (:require [clojure.tools.logging :as log]))

(defn default
  "Sample handler for abrupt RabbitMQ connection shutdown not initialized by application."
  [cause]
  (when-not (.isInitiatedByApplication cause)
    (log/error cause "RMQ connection shut down due to error")))
