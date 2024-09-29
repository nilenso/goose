(ns goose.brokers.rmq.return-listener
  (:require
   [goose.utils :as u]

   [clojure.tools.logging :as log]))

(defn default
  "Sample handler for unroutable messages."
  [msg]
  (log/error "Message returned from rabbitmq" msg))

(defn ^:no-doc wrapper
  [return-listener]
  (fn [reply-code reply-text exchange routing-key properties body]
    (return-listener {:reply-code  reply-code
                      :reply-text  reply-text
                      :exchange    exchange
                      :routing-key routing-key
                      :properties  properties
                      :body        (u/decode body)})))
