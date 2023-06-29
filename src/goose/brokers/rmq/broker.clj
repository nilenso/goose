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
  "Closes connection to RabbitMQ Message Broker."
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
    [this schedule-epoch-ms job]
    (rmq-scheduler/run-at (u/random-element (:channels this))
                          (assoc (:queue-type this) :queue (job/ready-queue job))
                          (:publisher-confirms this)
                          schedule-epoch-ms
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
  "Map of sample config for RabbitMQ Message Broker.

  ### Keys
  `:settings`           : Map of settings accepted by `langohr.core/settings`.\\
  [Connecting to RabbitMQ using Langohr](http://clojurerabbitmq.info/articles/connecting.html)

  `:queue-type`         : `classic` or `quorum` (for replication purpose).\\
  Example               : [[goose.brokers.rmq.queue/classic]], [[goose.brokers.rmq.queue/quorum]]

  `:publisher-confirms` : Strategy for RabbitMQ Publisher Confirms.\\
  [Publisher Confirms wiki](https://www.rabbitmq.com/confirms.html#publisher-confirms)\\
  [Publisher Confirms tutorial](https://www.rabbitmq.com/tutorials/tutorial-seven-java.html)

  `:return-listener`    : Handle unroutable messages.\\
  Receives a map of keys `:reply-code` `:reply-text` `:exchange` `:routing-key` `:properties` `:body`.\\
  Example               : [[return-listener/default]]

  `:shutdown-listener`  : Handle abrupt RabbitMQ connection shutdowns not initialized by application.
  Example               : [[shutdown-listener/default]]"
  {:settings           {:uri d/rmq-default-url}
   :queue-type         rmq-queue/classic
   :publisher-confirms publisher-confirms/sync
   :return-listener    return-listener/default
   :shutdown-listener  shutdown-listener/default})

(defn new-producer
  "Creates a RabbitMQ broker implementation for client.

  ### Args
  `opts`      : Map of `:settings`, `:queue-type`, `:publisher-confirms`, `:return-listener`, `:shutdown-listener`.\\
  Example     : [[default-opts]]

  `channels`  : Count of channel-pool-size for publishing messages.

  ### Usage
  ```Clojure
  (new-producer rmq-opts)
  ```

  - [RabbitMQ Message Broker wiki](https://github.com/nilenso/goose/wiki/RabbitMQ)"
  ([opts]
   (new-producer opts d/rmq-producer-channels))
  ([{:keys [queue-type publisher-confirms] :as opts}
    channels]
   (specs/assert-rmq-producer opts channels)
   (let [[rmq-conn channels] (rmq-connection/open opts channels)]
     (->RabbitMQ rmq-conn channels queue-type publisher-confirms nil))))

(defn new-consumer
  "Creates a RabbitMQ broker implementation for worker.

  ### Args
  `opts`  : Map of `:settings`, `:queue-type`, `:publisher-confirms`, `:return-listener`, `:shutdown-listener`.\\
  Example : [[default-opts]]

  ### Usage
  ```Clojure
  (new-consumer rmq-opts)
  ```

  - [RabbitMQ Message Broker wiki](https://github.com/nilenso/goose/wiki/RabbitMQ)"
  [opts]
  (specs/assert-rmq-consumer opts)
  ;; Connection to RabbitMQ is opened/closed from start/stop functions of worker.
  ;; Reason #1: Job-execution thread-pool must be given when starting RMQ connection.
  ;; Reason #2: Avoid duplication of code & mis-match in `threads` config.
  (->RabbitMQ nil nil nil nil opts))
