(ns goose.job
  ^:no-doc
  (:require
    [goose.metrics.keys :as metrics-keys]
    [goose.utils :as u]))

(defn new
  [execute-fn-sym args queue ready-queue retry-opts]
  {:id             (str (random-uuid))
   :execute-fn-sym execute-fn-sym
   :args           args
   ;; Since ready-queue is an internal implementation detail,
   ;; we store queue as well for find-by-pattern API queries.
   :queue          queue
   :ready-queue    ready-queue
   :retry-opts     retry-opts
   :enqueued-at    (u/epoch-time-ms)})

(defn ready-queue
  [job]
  (if (get-in job [:state :error])
    (or (get-in job [:retry-opts :ready-retry-queue]) (:ready-queue job))
    (:ready-queue job)))

(defn description
  "A job description is a description of how the job
  should be created. It's a job without the id or enqueued-at."
  [execute-fn-sym args queue ready-queue retry-opts]
  {:execute-fn-sym execute-fn-sym
   :args           args
   ;; Since ready-queue is an internal implementation detail,
   ;; we store queue as well for find-by-pattern API queries.
   :queue          queue
   :ready-queue    ready-queue
   :retry-opts     retry-opts})

(defn from-description
  [job-description]
  (assoc job-description
    :id (str (random-uuid))
    :enqueued-at (u/epoch-time-ms)))

(defn- calculate-latency
  [job]
  (cond
    (:retry-at (:state job))
    [metrics-keys/retry-latency (- (u/epoch-time-ms) (:retry-at (:state job)))]
    (:schedule job)
    [metrics-keys/schedule-latency (- (u/epoch-time-ms) (:schedule job))]
    (:cron-run-at job)
    [metrics-keys/cron-schedule-latency (- (u/epoch-time-ms) (:cron-run-at job))]
    :else
    [metrics-keys/execution-latency (- (u/epoch-time-ms) (:enqueued-at job))]))

(defn wrap-latency
  [next]
  (fn [opts job]
    (let [job (assoc job :latency (calculate-latency job))]
      (next opts job))))
