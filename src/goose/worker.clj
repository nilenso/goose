(ns goose.worker
  (:require
    [goose.brokers.broker :as b]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]))

(defprotocol Shutdown
  "Shutdown a worker object."
  (stop [_]))

(def default-opts
  "Default config for Goose worker."
  {:threads               5
   :queue                 d/default-queue
   :graceful-shutdown-sec 30
   :middlewares           nil
   :error-service-cfg     nil
   :metrics-plugin        (statsd/new statsd/default-opts)})

(defn start
  "Starts a threadpool for worker."
  [{:keys [broker] :as opts}]
  (b/start broker opts))
