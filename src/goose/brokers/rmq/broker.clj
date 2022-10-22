(ns goose.brokers.rmq.broker
  (:require
    [goose.broker :as b]
    [goose.brokers.rmq.api.dead-jobs :as dead-jobs]
    [goose.brokers.rmq.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.connection :as rmq-connection]
    [goose.brokers.rmq.publisher-confirms :as publisher-confirms]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.brokers.rmq.return-listener :as return-listener]
    [goose.brokers.rmq.scheduler :as rmq-scheduler]
    [goose.brokers.rmq.shutdown-listener :as shutdown-listener]
    [goose.brokers.rmq.worker :as rmq-worker]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.specs :as specs]
    [goose.utils :as u]))

(defprotocol Close
  "Close connections for RabbitMQ broker."
  (close [this]))

(defrecord RabbitMQ [rmq-conn channels queue-type publisher-confirms opts]
  b/Broker

  (enqueue
    [this job]
    (rmq-cmds/enqueue-back (u/random-element (:channels this))
                           (assoc (:queue-type this) :queue (job/ready-queue job))
                           (:publisher-confirms this)
                           job))

  (schedule
    [this schedule job]
    (rmq-scheduler/run-at (u/random-element (:channels this))
                          (assoc (:queue-type this) :queue (job/ready-queue job))
                          (:publisher-confirms this)
                          schedule
                          job))

  (start-worker [this worker-opts]
    (rmq-worker/start (merge worker-opts (:opts this))))

  ;; enqueued-jobs API
  (enqueued-jobs-size [this queue]
    (enqueued-jobs/size (u/random-element (:channels this)) queue))
  (enqueued-jobs-purge [this queue]
    (enqueued-jobs/purge (u/random-element (:channels this)) queue))

  ;; dead-jobs API
  (dead-jobs-size [this]
    (dead-jobs/size (u/random-element (:channels this))))
  (dead-jobs-pop [this]
    (dead-jobs/pop (u/random-element (:channels this))))
  (dead-jobs-replay-n-jobs
    [this n]
    (dead-jobs/replay-n-jobs (u/random-element (:channels this))
                             (:queue-type this)
                             (:publisher-confirms this)
                             n))
  (dead-jobs-purge [this]
    (dead-jobs/purge (u/random-element (:channels this))))

  Close
  (close [this]
    (rmq-connection/close (:rmq-conn this))))

(def default-opts
  "Default config for RabbitMQ client.
  Refer to http://clojurerabbitmq.info/articles/connecting.html
  for complete set of settings."
  {:settings           {:uri d/rmq-default-url}
   :queue-type         rmq-queue/classic
   :publisher-confirms publisher-confirms/sync
   :return-listener    return-listener/default
   :shutdown-listener  shutdown-listener/default})

(defn new-producer
  "Create a client that produce messages to RabbitMQ broker."
  ([opts]
   (new-producer opts d/rmq-producer-channels))
  ([{:keys [queue-type publisher-confirms] :as opts}
    channels]
   (specs/assert-rmq-producer opts channels)
   (let [[rmq-conn channels] (rmq-connection/open opts channels)]
     (->RabbitMQ rmq-conn channels queue-type publisher-confirms nil))))

(defn new-consumer
  "Create a RabbitMQ broker implementation for worker.
  The connection is opened & closed with start & stop of worker.
  Job-execution thread-pool must be given when starting RMQ connection.
  To avoid duplication & mis-match in `threads` config,
  we decided to delegate connection creation at start time of worker."
  [opts]
  (specs/assert-rmq-consumer opts)
  (->RabbitMQ nil nil nil nil opts))
