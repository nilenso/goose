(ns goose.validations.client
  (:require
    [goose.utils :as u]
    [goose.validations.redis :refer [validate-redis]]
    [goose.validations.queue :refer [validate-queue]]
    [clojure.edn :as edn]))

(defn- fn-symbol-unqualified?
  "Returns true if fn-symbol is unqualified."
  [sym]
  (not (qualified-symbol? sym)))

(defn- fn-symbol-unresolvable?
  "Returns true if fn-symbol is unresolvable."
  [sym]
  (not (resolve sym)))

(defn- args-unserializable?
  "Returns true if args are unserializable by edn.
  BUG-FIX/Edge-cases:
  - edn is serializing symbolized functions also to a list.
  - TODO: Modify args validation to be an exhaustive list.
  - Refer Sidekiq best practices on job params."
  ; iterate over args. Continue if byte, int, etc.
  ; Get full list from here: https://github.com/ptaoussanis/nippy
  ; if map/hash-set, recursion.
  [args]
  (try
    (not (= args (edn/read-string (str args))))
    (catch Exception _
      true)))

(defn- retries-negative?
  "Returns true if retries are negative."
  [num]
  (neg? num))

(defn validate-async-params
  [redis-url redis-pool-opts queue retries fn-sym args]
  (validate-redis redis-url redis-pool-opts)
  (validate-queue queue)
  (when-let
    [validation-error
     (cond
       (retries-negative? retries)
       ["Called with negative retries" (u/wrap-error :negative-retries retries)]

       (fn-symbol-unqualified? fn-sym)
       ["Called with unqualified function" (u/wrap-error :unqualified-fn fn-sym)]

       (fn-symbol-unresolvable? fn-sym)
       ["Called with unresolvable function" (u/wrap-error :unresolvable-fn fn-sym)]

       (args-unserializable? args)
       ["Called with unserializable args" (u/wrap-error :unserializable-args args)])]
    (throw (apply ex-info validation-error))))
