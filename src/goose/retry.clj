(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-error-handler
  [job ex]
  (log/error ex "Job execution failed." job))

(defn default-death-handler
  [job ex]
  (log/error ex "Job retries exhausted." job))

(defn default-retry-delay-sec
  [retry-count]
  ; TODO: fill this
  (* 2 retry-count))

(def default-opts
  {:max-retries            0
   :retry-delay-sec-fn-sym `default-retry-delay-sec
   :retry-queue            nil
   :error-handler-fn-sym   `default-error-handler
   :skip-dead-queue        false
   :death-handler-fn-sym   `default-death-handler})

(defn- update-opts
  [opts ex]
  (if (:first-failed-at opts)
    (assoc opts
      :last-retried-at (u/epoch-time-ms)
      :retry-count (inc (:retry-count opts))
      :error ex)
    (assoc opts
      :first-failed-at (u/epoch-time-ms)
      :retry-count 0
      :error ex)))

(defn- dead-job
  [redis-conn job]
  (let [opts (:retry-opts job)
        death-handler-fn-sym (:death-handler-fn-sym opts)
        error (:error opts)
        skip-dead-queue (:skip-dead-queue opts)
        last-retried-at (:last-retried-at opts)]
    (u/log-on-exceptions
      ((u/require-resolve death-handler-fn-sym) job error))
    (if-not skip-dead-queue
      (r/enqueue-sorted-set redis-conn d/dead-queue last-retried-at job))))

(defn failed-job
  [redis-conn job ex]
  (let [opts (:retry-opts job)
        max-retries (:max-retries opts)
        retry-delay-sec-fn-sym (:retry-delay-sec-fn-sym opts)
        error-handler-fn-sym (:error-handler-fn-sym opts)

        updated-opts (update-opts opts ex)
        retry-job (assoc job :retry-opts updated-opts)

        retry-count (:retry-count update-opts)
        retry-delay-sec ((u/require-resolve retry-delay-sec-fn-sym) retry-count)
        retry-at (+ (* 1000 retry-delay-sec) (u/epoch-time-ms))]
    (u/log-on-exceptions
      ((u/require-resolve error-handler-fn-sym) retry-job ex))
    (if (< retry-count max-retries)
      (r/enqueue-sorted-set redis-conn d/retry-queue retry-at retry-job)
      (dead-job redis-conn job))))
