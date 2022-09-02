(ns goose.job
  {:no-doc true}
  (:require
    [goose.metrics.keys :as metrics-keys]
    [goose.utils :as u]))

(defn new
  [execute-fn-sym args queue prefixed-queue retry-opts]
  {:id             (str (random-uuid))
   :execute-fn-sym execute-fn-sym
   :args           args
   :queue          queue
   :prefixed-queue prefixed-queue
   :retry-opts     retry-opts
   :enqueued-at    (u/epoch-time-ms)})

(defn execution-queue
  [job]
  (if (get-in job [:state :error])
    (or (get-in job [:retry-opts :prefixed-retry-queue]) (:prefixed-queue job))
    (:prefixed-queue job)))

(defn- calculate-latency
  [job]
  (cond
    (:retry-at (:state job))
    [metrics-keys/retry-latency (- (u/epoch-time-ms) (:retry-at (:state job)))]
    (:schedule job)
    [metrics-keys/schedule-latency (- (u/epoch-time-ms) (:schedule job))]
    :else
    [metrics-keys/execution-latency (- (u/epoch-time-ms) (:enqueued-at job))]))

(defn wrap-latency
  [next]
  (fn [opts job]
    (let [job (assoc job :latency (calculate-latency job))]
      (next opts job))))
