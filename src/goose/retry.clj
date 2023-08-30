(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-error-handler
  "Sample error handler of a Job.\\
  Called when a job fails.\\
  Logs exception & job details."
  [_error-service-config job ex]
  (log/error ex "Job execution failed." job))

(defn default-death-handler
  "Sample death handler of a Job.\\
  Called when a job dies, i.e. fails & has exhausted retries.\\
  Logs exception & job details."
  [_error-service-config job ex]
  (log/error ex "Job retries exhausted." job))

(defn default-retry-delay-sec
  "Calculates backoff seconds before a failed Job is retried."
  [retry-count]
  (+ 20
     (* (rand-int 20) (inc retry-count))
     (reduce * (repeat 4 retry-count)))) ; retry-count^4

(defn max-retries-reached?
  [{{:keys [retry-count]} :state
    {:keys [max-retries]} :retry-opts}]
  (>= retry-count max-retries))

(def default-opts
  "Map of sample configs for error handling & retries.\\
  A Job is considered to fail when it throws an exception during execution.\\
  A Job is considered dead when it has exhausted `max-retries`

  #### Mandatory Keys
  `:max-retries`            : Number of times to retry before marking a Job as dead.

  `:retry-delay-sec-fn-sym` : A fully-qualified function symbol to calculate backoff
   time in seconds before retrying a Job.\\
  Takes `retry-count` as input & returns backoff-seconds as a positive integer.\\
  *Example*                 : [[default-retry-delay-sec]]

  `:error-handler-fn-sym`   : A fully-qualified function symbol called when a Job fails.\\
  Takes `error-service-config`, `job` & `exception` as input.\\
  *Example*                 : [[default-error-handler]]

  `:death-handler-fn-sym`   : A fully-qualified function symbol called when a Job dies.\\
  Takes `error-service-config`, `job` & `exception` as input.\\
  *Example*                 : [[default-death-handler]]

  `:skip-dead-queue`        : Boolean flag for skipping Dead Jobs queue.

  #### Optional Keys
  `:retry-queue`            : Optional queue for retrying failed jobs in a separate queue.\\
  This helps increase/decrease priority of retrying failed jobs.\\
  **Note**: When using different retry queue, remember to start a worker process for that."
  {:max-retries            27
   :retry-delay-sec-fn-sym `default-retry-delay-sec
   :retry-queue            nil
   :error-handler-fn-sym   `default-error-handler
   :death-handler-fn-sym   `default-death-handler
   :skip-dead-queue        false})

(defn- prefix-retry-queue
  [retry-opts]
  (if-let [retry-queue (:retry-queue retry-opts)]
    (assoc retry-opts :ready-retry-queue (d/prefix-queue retry-queue))
    retry-opts))

(defn ^:no-doc prefix-queue-if-present
  [opts]
  (->> opts
       (prefix-retry-queue)))

(defn- failure-state
  [{{:keys [retry-count first-failed-at]} :state
    :as                                   _job}
   ex]
  {:error           (str ex)
   :last-retried-at (when first-failed-at (u/epoch-time-ms))
   :first-failed-at (or first-failed-at (u/epoch-time-ms))
   ;; `retry-count` denotes the count of job retries ,NOT the count job executions.
   ;; Maximum possible job executions = max-retries + 1.
   ;; 5 `max-retries` means a job will be retried upto 5 times, AFTER failure in first execution.
   ;; In total, a job will be executed 6 times before being marked as dead.
   ;; 0 `max-retries` means a job will be marked as dead, IF it fails in first execution.
   :retry-count     (if retry-count (inc retry-count) 0)})

(defn ^:no-doc set-failed-config
  [job ex]
  (assoc job :state (failure-state job ex)))
