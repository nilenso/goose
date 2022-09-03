(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-error-handler
  "Default error handler of a Job.
  Called when a job fails.
  Logs exception & job details."
  [_ job ex]
  (log/error ex "Job execution failed." job))

(defn default-death-handler
  "Default death handler of a Job
  Called when a job fails & has exhausted retries.
  Logs exception & job details."
  [_ job ex]
  (log/error ex "Job retries exhausted." job))

(defn default-retry-delay-sec
  "Calculates backoff seconds
  before a failed Job is retried."
  [retry-count]
  (+ 20
     (* (rand-int 20) (inc retry-count))
     (reduce * (repeat 4 retry-count)))) ; retry-count^4

(def default-opts
  "Default config for Error Handling & Retries."
  {:max-retries            27
   :retry-delay-sec-fn-sym `default-retry-delay-sec
   :retry-queue            nil
   :error-handler-fn-sym   `default-error-handler
   :skip-dead-queue        false
   :death-handler-fn-sym   `default-death-handler})

(defn- prefix-retry-queue
  [retry-opts]
  (if-let [retry-queue (:retry-queue retry-opts)]
    (assoc retry-opts :prefixed-retry-queue (d/prefix-queue retry-queue))
    retry-opts))

(defn ^:no-doc prefix-queue-if-present
  [opts]
  (->> opts
       (prefix-retry-queue)))

(defn- failure-state
  [{{:keys [retry-count first-failed-at]} :state} ex]
  {:error           (str ex)
   :last-retried-at (when first-failed-at (u/epoch-time-ms))
   :first-failed-at (or first-failed-at (u/epoch-time-ms))
   :retry-count     (if retry-count (inc retry-count) 0)})

(defn set-failed-config
  [job ex]
  (assoc
    job :state
        (failure-state job ex)))
