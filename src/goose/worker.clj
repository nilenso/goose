(ns goose.worker
  (:require
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]
    [goose.specs :as specs]))

(defprotocol Shutdown
  ;; We're extending a protocol via metadata because reloading REPL
  ;; nullifies all existing defrecord implementations.
  "Gracefully shuts down a worker."
  :extend-via-metadata true
  (stop [this] "Stops a worker process in following steps:
  - Signal worker thread-pool to shutdown
  - Await for graceful-shutdown seconds for in-progress jobs to complete
  - Forcibly shutdown worker thread-pool"))

(def default-opts
  "Map of sample configs for consuming jobs.

  #### Mandatory Keys

  `:broker`                : Message broker that transfers message from Producer to Consumer.\\
  Given value must implement [[goose.broker/Broker]] protocol.\\
  [Message Broker wiki](https://github.com/nilenso/goose/wiki/Message-Brokers)

  `:threads`               : Count of thread-pool-size for executing jobs.

  `:queue`                 : Queue from which to consume jobs for execution.

  `:graceful-shutdown-sec` : Waiting time for in-progress jobs to complete during shutdown.

  `:metrics-plugin`        : Publish Goose metrics to respective backend.\\
  Example                  : [[statsd/StatsD]]\\
  Given value must implement [[goose.metrics/Metrics]] protocol.

  #### Optional Keys

  `:middlewares`          : Chain of function/s to run 'around' execution of a Job
  [Middlewares wiki](https://github.com/nilenso/goose/wiki/Middlewares)

  `:error-service-config` : Config for error service like Honeybadger, Sentry, etc.\\
  [Error Handling & Retries wiki](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries)"
  {:threads               d/worker-threads
   :queue                 d/default-queue
   :graceful-shutdown-sec d/graceful-shutdown-sec
   :metrics-plugin        (statsd/new statsd/default-opts)})

(defn start
  "Starts a worker process that does multiple things including, but not limited to:
  - Consuming & execution of jobs from given queue
  - Enqueuing scheduled jobs due for execution
  - Retry failed jobs & mark them as dead when retries are exhausted
  - Send metrics around Job execution & state of message broker

  Args:

  `opts`  : Map of `:threads`, `:queue`, `:graceful-shutdown-sec`,
   `:metrics-plugin`, `:middleware` & `:error-service-config`.\\
  Example : [[default-opts]]

  Usage:
  ```Clojure
  (let [worker (start worker-opts)]
    ;; When shutting down worker...
    (stop worker))
  ```"
  [{:keys [broker] :as opts}]
  (specs/assert-worker opts)
  (b/start-worker broker opts))
