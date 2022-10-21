(ns goose.worker
  (:require
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]))

(defprotocol Shutdown
  ;; We're extending a protocol via metadata because reloading REPL
  ;; nullifies all existing defrecord implementations.
  "Shutdown a worker object."
  :extend-via-metadata true
  (stop [this]))

(def default-opts
  "Default config for Goose worker."
  {:threads               d/worker-threads
   :queue                 d/default-queue
   :graceful-shutdown-sec d/graceful-shutdown-sec
   :metrics-plugin        (statsd/new statsd/default-opts)})

(defn start
  "Starts a threadpool for worker."
  [{:keys [broker] :as opts}]
  (b/start-worker broker opts))
