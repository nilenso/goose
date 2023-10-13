(ns ^:no-doc goose.job
  (:require
    [goose.metrics :as m]
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

(defn retried? [job]
  (boolean (get-in job [:state :error])))

(defn ready-or-retry-queue
  [job]
  (if (retried? job)
    (or (get-in job [:retry-opts :ready-retry-queue]) (:ready-queue job))
    (:ready-queue job)))

(defn description
  "A job description is a description of how the job
  should be created. It is a job without the id or enqueued-at."
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
    [m/retry-latency (- (u/epoch-time-ms) (:retry-at (:state job)))]
    (:schedule-run-at job)
    [m/schedule-latency (- (u/epoch-time-ms) (:schedule-run-at job))]
    (:cron-run-at job)
    [m/cron-schedule-latency (- (u/epoch-time-ms) (:cron-run-at job))]
    :else
    [m/execution-latency (- (u/epoch-time-ms) (:enqueued-at job))]))

(defn wrap-latency
  [next]
  (fn [opts job]
    (let [job (assoc job :latency (calculate-latency job))]
      (next opts job))))
