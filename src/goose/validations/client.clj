(ns goose.validations.client
  (:require
    [goose.utils :as u]
    [goose.validations.redis :refer [validate-redis]]
    [goose.validations.queue :refer [validate-queue]]
    [goose.validations.scheduler :refer [validate-schedule]]
    [goose.validations.retry :refer [validate-retry]]
    [clojure.edn :as edn]))

(defn- args-unserializable?
  "Returns true if args are unserializable by edn.
  Pending BUG: https://github.com/nilenso/goose/issues/9"
  [args]
  (try
    (not (= args (edn/read-string (str args))))
    (catch Exception _
      true)))

(defn validate-async-params
  [redis-url redis-pool-opts
   queue schedule-opts retry-opts execute-fn-sym args]
  (validate-redis redis-url redis-pool-opts)
  (validate-queue queue)
  (validate-schedule schedule-opts)
  (validate-retry retry-opts)
  (when-let
    [validation-error
     (cond
       (not (qualified-symbol? execute-fn-sym))
       ["Function symbol should be Qualified" (u/wrap-error :unqualified-fn execute-fn-sym)]

       (not (resolve execute-fn-sym))
       ["Function symbol should be Resolvable" (u/wrap-error :unresolvable-fn execute-fn-sym)]

       (args-unserializable? args)
       ["Args should be Serializable" (u/wrap-error :unserializable-args args)])]
    (throw (apply ex-info validation-error))))
