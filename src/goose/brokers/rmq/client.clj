(ns goose.brokers.rmq.client
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.rmq.commands :as rmq-cmds]

    [taoensso.nippy :as nippy]
    [langohr.core :as rmq]
    [langohr.channel :as lch]
    [langohr.queue :as lq]
    [langohr.consumers :as lc]
    [langohr.basic :as lb]
    [goose.defaults :as d]
    [clojure.tools.logging :as log]))

(defrecord RabbitMQ [conn ch]
  b/Broker
  (enqueue [this job]
    (rmq-cmds/enqueue-back (:ch this) job)))

(defmethod b/new d/rmq new-rmq-broker
  ([{:keys [settings]}]
   (let [conn (rmq/connect settings)
         ch (lch/open conn)]
     (RabbitMQ. conn ch))))
