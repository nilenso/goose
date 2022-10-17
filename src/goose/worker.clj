(ns goose.worker
  (:require
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]))

(defprotocol Shutdown
  "Shutdown a worker object."
  (stop [this]))

(def default-opts
  "Default config for Goose worker."
  {:threads               d/worker-threads
   :queue                 d/default-queue
   :graceful-shutdown-sec d/graceful-shutdown-sec
   :metrics-plugin        (statsd/new statsd/default-opts)
   :middlewares           nil
   :error-service-cfg     nil})

(defn start
  "Starts a threadpool for worker."
  [{:keys [broker] :as opts}]
  (b/start broker opts))
