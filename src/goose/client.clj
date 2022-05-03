(ns goose.client
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.edn :as edn]))

(defn- qualified-fn-symbol?
  "A function must be a qualified symbol."
  [s]
  (and
    (qualified-symbol? s)
    (resolve s)))

(defn- edn-serializable-args?
  "Returns true if args are edn-serializable.
  BUG-FIX/Edge-cases:
  - edn is serializing symbolized functions to a list.
  - TODO: Modify args validation to have an exhaustive list.
  - Refer Sidekiq best practices on job params."
  [args]
  (= args
     (edn/read-string (str args))))

(defn- non-negative-retries?
  "Returns true if num is non-negative."
  [num]
  (not (neg? num)))

(def default-queue "goose/queue:default")

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  Usage:
  - (async `foo)
  - (async `foo {:args '(:bar) :retries 2})
  Validations:
  - A function must be a qualified symbol
  - Args must be edn-serializable
  - Retries must be non-negative
  edn: https://github.com/edn-format/edn"
  [qualified-fn-symbol & {:keys [args retries]
                          :or   {args    nil
                                 retries 0}}]
  {:pre [(qualified-fn-symbol? qualified-fn-symbol)
         (edn-serializable-args? args)
         (non-negative-retries? retries)]}
  (r/wcar* (car/rpush default-queue [qualified-fn-symbol args])))