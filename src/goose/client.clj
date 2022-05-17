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

(defn client-opts
  [& {:keys [redis-url
             redis-pool-opts
             retries]
      :or   {redis-url       "redis://localhost:6379"
             redis-pool-opts {}
             retries         0}}]
  {:redis-conn
   {:pool redis-pool-opts
    :spec {:uri redis-url}}
   :retries retries})

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
  ([opts resolvable-fn-symbol & args]
   (validate-async-params opts resolvable-fn-symbol args)
   (let [job {:id          (job-id)
              :fn-sym      resolvable-fn-symbol
              :args        args
              :retries     (:retries opts)
              :enqueued-at (epoch-time)}]
     (enqueue (:redis-conn opts) job))))


