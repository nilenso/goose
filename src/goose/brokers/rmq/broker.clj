(ns goose.brokers.rmq.broker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.rmq.channel :as channels]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.scheduler :as rmq-scheduler]
    [goose.brokers.rmq.worker :as rmq-worker]
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.core :as lcore]))

(defprotocol Close
  "Close connections for RabbitMQ broker."
  (close [_]))

(defrecord RabbitMQ [conn channels]
  b/Broker
  (enqueue [this job]
    (rmq-cmds/enqueue-back (u/random-element (:channels this)) job))
  (schedule [this schedule job]
    (rmq-scheduler/run-at (:channels this) schedule job))
  (start [this worker-opts]
    (rmq-worker/start (assoc worker-opts :rmq-conn (:conn this))))

  Close
  (close [this]
    ; Channels get closed automatically when connection is closed.
    (lcore/close (:conn this))))

(def default-opts
  "Default config for RabbitMQ client.
  Refer to http://clojurerabbitmq.info/articles/connecting.html
  for complete set of settings."
  {:settings {:uri d/rmq-default-url}})

(defn new
  "Create a client for RabbitMQ broker.
  When enqueuing jobs, channel-pool-size MUST be defined.
  When executing jobs, channel-pool-size should not be given
  as worker creates channels equal to number of threads."
  ([opts]
   (goose.brokers.rmq.broker/new opts 0))
  ([{:keys [settings]} channel-pool-size]
   (let [conn (lcore/connect settings)
         channel-pool (channels/new-pool conn channel-pool-size)]
     (->RabbitMQ conn channel-pool))))
