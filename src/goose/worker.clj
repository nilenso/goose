(ns goose.worker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.client :as redis-client]
    [goose.defaults :as d]
    [goose.statsd :as statsd]))

(defprotocol Shutdown
  "Shutdown a worker object."
  (stop [_]))

(def default-opts
  "Default config for Goose worker."
  {:broker-opts                    redis-client/default-opts
   :threads                        1
   :queue                          d/default-queue
   :scheduler-polling-interval-sec 5
   :graceful-shutdown-sec          30
   :middlewares                    nil
   :error-service-cfg              nil
   :statsd-opts                    statsd/default-opts})

(defn start
  "Starts a threadpool for worker."
  [{:keys [broker-opts statsd-opts]
    :as opts}]
  (let [broker (b/new broker-opts)]

    (statsd/initialize statsd-opts)
    (let [shutdown-fn (b/start broker opts)]
      (reify Shutdown
        (stop [_] (shutdown-fn))))))
