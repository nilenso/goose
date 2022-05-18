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

(defn- enqueue [conn job]
  (try
    (r/wcar* conn (car/rpush cfg/default-queue job))
    (catch Exception e
      (throw
        (ex-info
          "Error enqueuing to redis"
          {:errors {:redis-error (.getMessage e)}}))))
  (:id job))

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
  [{:keys [redis-url
           redis-pool-opts
           retries]
    :or   {redis-url       cfg/default-redis-url
           redis-pool-opts {}
           retries         0}}
   resolvable-fn-symbol
   & args]
  (validate-async-params
    redis-url
    redis-pool-opts
    retries
    resolvable-fn-symbol
    args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        job {:id          (job-id)
             :fn-sym      resolvable-fn-symbol
             :args        args
             :retries     retries
             :enqueued-at (epoch-time)}]
    (enqueue redis-conn job)))
