(ns goose.brokers.rmq.broker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.rmq.channel :as channels]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.worker :as rmq-worker]
    [goose.utils :as u]

    [langohr.core :as lcore]))

(defprotocol Close
  "Close connections for RabbitMQ broker."
  (close [this]))

(defrecord RabbitMQ [conn channels]
  b/Broker
  (enqueue [this job]
    (rmq-cmds/enqueue-back (u/get-one (:channels this)) job))
  (start [this worker-opts]
    (rmq-worker/start (assoc worker-opts :rmq-conn (:conn this))))

  Close
  (close [this]
    (for [ch (:channels this)]
      (lcore/close ch))
    (lcore/close (:conn this))))

(def default-opts
  "Default config for RabbitMQ client.
  Refer to http://clojurerabbitmq.info/articles/connecting.html
  for complete set of settings."
  {:settings {:uri "amqp://guest:guest@localhost:5672/"}})

(defn new
  "Create a client for RabbitMQ broker.
  When enqueuing, channel-count MUST be provided.
  When executing jobs using Goose worker,
  channel-count need not be given as
  channels are created equal to thread-count."
  ([opts]
   (goose.brokers.rmq.broker/new opts 0))
  ([{:keys [settings]} channel-count]
   (let [conn (lcore/connect settings)
         channel-pool (channels/new conn channel-count)]
     (->RabbitMQ conn channel-pool))))
