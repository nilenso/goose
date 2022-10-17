(ns goose.brokers.rmq.connection
  (:require
    [goose.brokers.rmq.channel :as rmq-channel]

    [langohr.core :as lcore]))

(defn open
  [{:keys [settings publisher-confirms return-listener shutdown-listener]}
   channel-pool-size]
  (let [rmq-conn (lcore/connect settings)
        channel-pool (rmq-channel/new-pool rmq-conn channel-pool-size publisher-confirms return-listener)]
    (lcore/add-shutdown-listener rmq-conn shutdown-listener)

    [rmq-conn channel-pool]))

(defn close
  [rmq-conn]
  ;; Channels get closed automatically when connection is closed.
  (when rmq-conn
    (lcore/close rmq-conn)))
