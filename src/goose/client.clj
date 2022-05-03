(ns goose.client
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.edn :as edn]))

(defn- validation-errors [name data]
  {:errors {name data}})

(defn- validate-resolvable-fn-symbol
  "A function must be a qualified symbol."
  [sym]
  (when-not
    (and (qualified-symbol? sym) (resolve sym))
    (throw
      (ex-info "Called with unresolvable function"
               (validation-errors :unresolvable-fn sym)))))

(defn- validate-args
  "Returns true if args are edn-serializable.
  BUG-FIX/Edge-cases:
  - edn is serializing symbolized functions to a list.
  - TODO: Modify args validation to have an exhaustive list.
  - Refer Sidekiq best practices on job params."
  [args]
  (when-not
    (= args (edn/read-string (str args)))
    (throw
      (ex-info "Called with unserializable args"
               (validation-errors :unserializable-args args)))))

(defn- validate-retries
  "Returns true if num is non-negative."
  [num]
  (when
    (neg? num)
    (throw
      (ex-info "Called with negative retries"
               (validation-errors :negative-retries num)))))

(defn- validate-async-params
  [resolvable-fn-symbol args retries]
  (validate-resolvable-fn-symbol resolvable-fn-symbol)
  (validate-args args)
  (validate-retries retries))

(def default-queue "goose/queue:default")

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
  [resolvable-fn-symbol & {:keys [args retries]
                           :or   {args    nil
                                  retries 0}}]
  (validate-async-params resolvable-fn-symbol args retries)
  ; serialize into a schema
  ; push to redis
  ; return job ID.
  (r/wcar* (car/rpush default-queue [resolvable-fn-symbol args])))