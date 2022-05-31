(ns goose.client
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.client :refer [validate-async-params]]))

(defn- job-id []
  (str (random-uuid)))

(defn- route-job
  [queue schedule]
  (if-let [run-at (scheduler/run-at schedule)]
    [(str d/queue-prefix d/schedule-queue) run-at]
    [(str d/queue-prefix queue)]))

(defn- push-job
  ([conn job queue]
   (r/enqueue-back conn queue job))
  ([conn job queue run-at]
   (if (< run-at (u/epoch-time-ms))
     (r/enqueue-front conn queue job)
     (r/enqueue-sorted-set conn queue run-at job))))

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}
   :queue           d/default-queue
   :schedule-opts   scheduler/default-opts
   :retries         0})

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  Usage:
  - (async `foo)
  - (async `foo {:args '(:bar) :retries 2})
  Validations:
  - A function must be a resolvable & a fully qualified symbol
  - Args must be edn-serializable
  - Retries must be non-negative
  edn: https://github.com/edn-format/edn"
  [{:keys [redis-url redis-pool-opts
           queue schedule-opts retries]}
   resolvable-fn-symbol
   & args]
  (validate-async-params
    redis-url redis-pool-opts
    queue schedule-opts retries
    resolvable-fn-symbol args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        job {:id          (job-id)
             :queue       queue
             :fn-sym      resolvable-fn-symbol
             :args        args
             :retries     retries
             :enqueued-at (u/epoch-time-ms)}
        queue-opts (route-job queue schedule-opts)
        push-job-params (concat [redis-conn job] queue-opts)]
    (apply push-job push-job-params)
    (:id job)))
