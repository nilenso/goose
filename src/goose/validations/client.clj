(ns goose.validations.client
  (:require
    [clojure.edn :as edn]))

(defn- wrap-error [name data]
  {:errors {name data}})

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
  (not (= args (edn/read-string (str args)))))

(defn- retries-negative?
  "Returns true if retries are negative."
  [num]
  (neg? num))

(defn validate-async-params
  [fn-sym args retries]
  (when-let [validation-error
        (cond
          (fn-symbol-unqualified? fn-sym)
          ["Called with unqualified function" (wrap-error :unqualified-fn fn-sym)]

          (fn-symbol-unresolvable? fn-sym)
          ["Called with unresolvable function" (wrap-error :unresolvable-fn fn-sym)]

          (args-unserializable? args)
          ["Called with unserializable args" (wrap-error :unserializable-args args)]

          (retries-negative? retries)
          ["Called with negative retries" (wrap-error :negative-retries num)])]

    (throw (apply ex-info validation-error))))

