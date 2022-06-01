(ns goose.validations.retry
  (:require
    [goose.utils :as u]))

(defn validate-retry
  [{:keys [max-retries error-handler-fn-sym]}]
  (comment
    (when-let
      [validation-error
       (cond
         (neg? max-retries)
         ["Max retry count shouldn't be negative" (u/wrap-error :negative-max-retries max-retries)]

         (not (int? max-retries))
         ["Max retry count should be an integer" (u/wrap-error :non-int-max-retries max-retries)]

         (not (qualified-symbol? error-handler-fn-sym))
         ["Error handler should be Qualified" (u/wrap-error :unqualified-error-handler error-handler-fn-sym)]

         (not (resolve error-handler-fn-sym))
         ["Error handler should be Resolvable" (u/wrap-error :unresolvable-error-handler error-handler-fn-sym)])]
      (throw (apply ex-info validation-error)))))
