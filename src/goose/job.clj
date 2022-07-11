(ns goose.job
  (:require
    [goose.statsd :as statsd]
    [goose.utils :as u]))

(defn new
  [execute-fn-sym args queue retry-opts]
  {:id             (str (random-uuid))
   :execute-fn-sym execute-fn-sym
   :args           args
   :queue          queue
   :retry-opts     retry-opts
   :enqueued-at    (u/epoch-time-ms)})

(defn- calculate-latency
  [job]
  (cond
    (:retry-at (:state job))
    [statsd/retry-latency (- (u/epoch-time-ms) (:retry-at (:state job)))]
    (:schedule job)
    [statsd/schedule-latency (- (u/epoch-time-ms) (:schedule job))]
    :else
    [statsd/execution-latency (- (u/epoch-time-ms) (:enqueued-at job))]))

(defn wrap-latency
  [call]
  (fn [opts job]
    (let [job (assoc job :latency (calculate-latency job))]
      (call opts job))))
