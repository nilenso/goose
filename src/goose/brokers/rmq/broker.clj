(ns goose.brokers.rmq.broker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.rmq.channels :as channels]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.worker :as rmq-worker]
    [langohr.core :as rmq]
    ))

(defprotocol Close
  "Close connections for RabbitMQ broker."
  (close [this]))

(defrecord RabbitMQ [conn pool]
  b/Broker
  (enqueue [this job]
    (rmq-cmds/enqueue-back (channels/get-one (:pool this)) job))
  (start [this worker-opts]
    (rmq-worker/start
      (assoc worker-opts
        :pool (:pool this))))

  Close
  (close [this]
    (channels/close-all (:pool this))
    (rmq/close (:conn this))))

(def default-opts
  "Default config for RabbitMQ client.
  Refer to http://clojurerabbitmq.info/articles/connecting.html
  for complete set of settings."
  {:settings      {:uri "amqp://guest:guest@localhost:5672/"}
   :channel-count 1})

(defn new
  "Create a client for RabbitMQ broker."
  ([{:keys [settings channel-count]}]
   (let [conn (rmq/connect settings)
         channels (channels/new conn channel-count)]
     (->RabbitMQ conn channels))))
