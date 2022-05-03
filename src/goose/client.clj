(ns goose.client
  (:require
    [goose.validations.async :as v]
    [goose.redis :as r]
    [taoensso.carmine :as car]))

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
  ([qualified-fn-symbol & {:keys [args retries]
                           :or   {args    nil
                                  retries 0}}]
   {:pre [(v/async qualified-fn-symbol args retries)]}
   (r/wcar* (car/rpush default-queue [qualified-fn-symbol args]))))