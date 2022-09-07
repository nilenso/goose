(ns goose.brokers.rmq.broker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.rmq.api.dead-jobs :as dead-jobs]
    [goose.brokers.rmq.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.rmq.channel :as channels]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.publisher-confirms :as publisher-confirms]
    [goose.brokers.rmq.scheduler :as rmq-scheduler]
    [goose.brokers.rmq.worker :as rmq-worker]
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.core :as lcore]))

(defprotocol Close
  "Close connections for RabbitMQ broker."
  (close [_]))

(defrecord RabbitMQ [conn channels publisher-confirms]
  b/Broker

  (enqueue [this job]
    (rmq-cmds/enqueue-back (u/random-element (:channels this))
                           (:publisher-confirms this)
                           job))

  (schedule [this schedule job]
    (rmq-scheduler/run-at (u/random-element (:channels this))
                          (:publisher-confirms this)
                          schedule
                          job))

  (start [this worker-opts]
    (rmq-worker/start (assoc worker-opts
                        :rmq-conn (:conn this)
                        :publisher-confirms (:publisher-confirms this))))

  ; enqueued-jobs API
  (enqueued-jobs-size [this queue]
    (enqueued-jobs/size (u/random-element (:channels this)) queue))
  (enqueued-jobs-purge [this queue]
    (enqueued-jobs/purge (u/random-element (:channels this)) queue))

  ; dead-jobs API
  (dead-jobs-size [this]
    (dead-jobs/size (u/random-element (:channels this))))
  (dead-jobs-pop [this]
    (dead-jobs/pop (u/random-element (:channels this))))
  (dead-jobs-purge [this]
    (dead-jobs/purge (u/random-element (:channels this))))

  Close
  (close [this]
    ; Channels get closed automatically when connection is closed.
    (lcore/close (:conn this))))

(def default-opts
  "Default config for RabbitMQ client.
  Refer to http://clojurerabbitmq.info/articles/connecting.html
  for complete set of settings."
  {:settings           {:uri d/rmq-default-url}
   :publisher-confirms publisher-confirms/sync-strategy})

(defn new
  "Create a client for RabbitMQ broker.
  When enqueuing jobs, channel-pool-size MUST be defined.
  When executing jobs, channel-pool-size should not be given
  as worker creates channels equal to number of threads."
  ([opts]
   (goose.brokers.rmq.broker/new opts 0))
  ([{:keys [settings publisher-confirms]} channel-pool-size]
   (let [conn (lcore/connect settings)
         channel-pool (channels/new-pool conn channel-pool-size publisher-confirms)]
     (->RabbitMQ conn channel-pool publisher-confirms))))
