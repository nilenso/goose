(ns goose.client
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]
    [goose.validations.client :refer [validate-async-params]]))

(defn- job-id []
  (str (random-uuid)))

(defn- schedule-time
  [{:keys [perform-at perform-in-sec]}]
  (cond
    perform-at
    (u/epoch-time perform-at)

    perform-in-sec
    (+ perform-in-sec (u/epoch-time))))

(defn- route-job
  [queue schedule]
  (if-let [delay (schedule-time schedule)]
    [(str d/queue-prefix d/schedule-queue) delay]
    [(str d/queue-prefix queue)]))

(defn- push-job
  ([conn job queue]
   (r/enqueue-back conn queue job))
  ([conn job queue time]
   (if (< time (u/epoch-time))
     (r/enqueue-front conn queue job)
     (r/enqueue-sorted-set conn queue time job))))

(def ^:private schedule-opts
  "perform-at & perform-in-sec opts are mutually exclusive."
  {:perform-at     nil
   :perform-in-sec nil})

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}
   :queue           d/default-queue
   :schedule        schedule-opts
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
           queue schedule retries]}
   resolvable-fn-symbol
   & args]
  (validate-async-params
    redis-url redis-pool-opts
    queue schedule retries
    resolvable-fn-symbol args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        job {:id          (job-id)
             :queue       queue
             :fn-sym      resolvable-fn-symbol
             :args        args
             :retries     retries
             :enqueued-at (u/epoch-time)}
        queue-opts (route-job queue schedule)
        push-job-params (concat [redis-conn job] queue-opts)]
    (apply push-job push-job-params)
    (:id job)))
