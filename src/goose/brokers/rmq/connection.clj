(ns goose.brokers.rmq.connection
  (:require [langohr.core :as lcore]
            [goose.brokers.rmq.channel :as rmq-channel]))

(defn open
  [{:keys [settings publisher-confirms return-listener-fn shutdown-listener-fn]}
   channel-pool-size]
  (let [rmq-conn (lcore/connect settings)
        channel-pool (rmq-channel/new-pool rmq-conn channel-pool-size publisher-confirms return-listener-fn)]
    (lcore/add-shutdown-listener rmq-conn shutdown-listener-fn)

    [rmq-conn channel-pool]))

(defn close
  [rmq-conn]
  ; Channels get closed automatically when connection is closed.
  (when rmq-conn
    (lcore/close rmq-conn)))
