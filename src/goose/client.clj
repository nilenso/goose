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

(defn- enqueue [opts job]
  (try
    ; TODO convert to threading macro.???
    (r/wcar* (:redis-conn opts) (car/rpush cfg/default-queue job))
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
  [opts resolvable-fn-symbol & {:keys [args retries]
                           :or   {args    nil
                                  retries 0}}]
  ; TODO: validate opts.
  (validate-async-params resolvable-fn-symbol args retries)
  (let
    [job {:id          (job-id)
          :fn-sym      resolvable-fn-symbol
          :args        args
          :retries     retries
          :enqueued-at (epoch-time)}]
    (enqueue opts job)))
