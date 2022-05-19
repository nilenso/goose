(ns goose.client
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [goose.validations.client :refer [validate-async-params]]
    [taoensso.carmine :as car]))

(defn- job-id []
  (str (random-uuid)))

(defn- epoch-time []
  (quot (System/currentTimeMillis) 1000))

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
           queue retries]
    :or   {redis-url       cfg/default-redis-url
           redis-pool-opts {}
           queue           cfg/default-queue
           retries         0}}
   resolvable-fn-symbol
   & args]
  (validate-async-params
    redis-url redis-pool-opts queue
    retries resolvable-fn-symbol args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        prefixed-queue (str cfg/queue-prefix queue)
        job {:id          (job-id)
             :fn-sym      resolvable-fn-symbol
             :args        args
             :retries     retries
             :enqueued-at (epoch-time)}]
    (r/enqueue redis-conn prefixed-queue job)
    (:id job)))
