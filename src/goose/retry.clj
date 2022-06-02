(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.job :as j]
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
  (+ 20
     (* (rand-int 20) (inc retry-count))
     (reduce * (repeat 4 retry-count)))) ; retry-count^4

(def default-opts
  {:max-retries            27
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

(defn- internal-opts
  [queue time]
  {:redis-fn goose.redis/enqueue-sorted-set
   :queue    queue
   :run-at   time})

(defn- update-retry-job
  [{{:keys [retry-count retry-delay-sec-fn-sym
            error error-handler-fn-sym]} :retry-opts
    :as                                  job}]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        queue (u/prefix-queue d/schedule-queue)
        retry-delay-sec ((u/require-resolve retry-delay-sec-fn-sym) retry-count)
        retry-at (u/add-sec retry-delay-sec)]
    (u/log-on-exceptions (error-handler job error))
    (assoc job
      :internal-opts (internal-opts queue retry-at))))

(defn- update-dead-job
  [{{:keys [last-retried-at skip-dead-queue
            error death-handler-fn-sym]} :retry-opts
    :as                                  job}]
  (let [death-handler (u/require-resolve death-handler-fn-sym)
        queue (u/prefix-queue d/dead-queue)
        dead-at (or last-retried-at (u/epoch-time-ms))]
    (u/log-on-exceptions (death-handler job error))
    (when-not skip-dead-queue
      (assoc job
        :internal-opts (internal-opts queue dead-at)))))

(defn- update-job
  [{{:keys [max-retries retry-count]} :retry-opts
    :as                               job}]
  (if (< retry-count max-retries)
    (update-retry-job job)
    (update-dead-job job)))

(defn failed-job
  [redis-conn job ex]
  (let [opts (update-opts (:retry-opts job) ex)
        retry-job (assoc job :retry-opts opts)
        internal-job (update-job retry-job)]
    (j/push redis-conn internal-job)))
