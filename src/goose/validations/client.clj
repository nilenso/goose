(ns goose.validations.client
  (:require
    [clojure.edn :as edn]))

(defn- validation-error [name data]
  {:errors {name data}})

(defn- fn-symbol-qualified?
  "Returns true if fn-symbol is unqualified."
  [sym]
  (not (qualified-symbol? sym)))

(defn- fn-symbol-resolvable?
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
  (let [validation-error
        (cond
          (fn-symbol-qualified? fn-sym)
          ["Called with unqualified function" (validation-error :unqualified-fn fn-sym)]

          (fn-symbol-resolvable? fn-sym)
          ["Called with unresolvable function" (validation-error :unresolvable-fn fn-sym)]

          (args-unserializable? args)
          ["Called with unserializable args" (validation-error :unserializable-args args)]

          (retries-negative? retries)
          ["Called with negative retries" (validation-error :negative-retries num)])]

    (when validation-error
      (throw (apply ex-info validation-error)))))