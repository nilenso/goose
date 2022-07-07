(ns goose.executor
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.statsd.statsd :as statsd]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn- record-latency
  [statsd-opts job]
  (cond
    (:retry-at (:state job))
    (statsd/timing statsd-opts "retry.latency" (- (u/epoch-time-ms) (:retry-at (:state job))))

    (:schedule job)
    (statsd/timing statsd-opts "scheduled.latency" (- (u/epoch-time-ms) (:schedule job)))

    :else
    (statsd/timing statsd-opts "execution.latency" (- (u/epoch-time-ms) (:enqueued-at job)))))

(defn- execute-job
  [{:keys [redis-conn statsd-opts]} {:keys [id execute-fn-sym args] :as job}]
  (let [statsd-opts (statsd/add-function-tag statsd-opts (str execute-fn-sym))
        sample-rate (:sample-rate statsd-opts)
        tags (:tags statsd-opts)]
    (record-latency statsd-opts job)
    (try
      (statsd/emit-metrics
        sample-rate tags
        (apply (u/require-resolve execute-fn-sym) args))
      (log/debug "Executed job-id:" id)
      (catch Exception ex
        (retry/handle-failure redis-conn job ex)))))

(defn preservation-queue
  [id]
  (str d/in-progress-queue-prefix id))

(defn run
  [{:keys [thread-pool redis-conn prefixed-queue in-progress-queue]
    :as   opts}]
  (log/debug "Long-polling broker...")
  (u/while-pool
    thread-pool
    (u/log-on-exceptions
      (when-let [job (r/dequeue-and-preserve redis-conn prefixed-queue in-progress-queue)]
        (execute-job opts job)
        (r/remove-from-list redis-conn in-progress-queue job)))))
