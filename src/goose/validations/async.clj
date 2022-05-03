(ns goose.validations.async
  (:require
    [clojure.edn :as edn]))

(defn qualified-fn-symbol?
  "A function must be a qualified symbol."
  [s]
  (and
    (qualified-symbol? s)
    (resolve s)))

(defn edn-serializable-args?
  "Returns true if args are edn-serializable."
  [args]
  (= args
     (edn/read-string (str args))))

(defn retries?
  "Returns true if num is non-negative."
  [num]
  (not (neg? num)))

(defn async
  "Validate fn-symbol, args & retries for goose.client/async."
  [qualified-fn-symbol args retries]
  (assert (qualified-fn-symbol? qualified-fn-symbol))
  (assert (edn-serializable-args? args))
  (assert (retries? retries))
  true)
