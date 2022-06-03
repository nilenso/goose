(ns goose.validations.retry
  (:require
    [goose.utils :as u]
    [goose.validations.queue :refer [validate-queue]]))

(defn validate-retry
  [{:keys [max-retries retry-delay-sec-fn-sym
           retry-queue skip-dead-queue
           error-handler-fn-sym death-handler-fn-sym]}]
  (when retry-queue (validate-queue retry-queue))
  (when-let
    [validation-error
     (cond
       (neg? max-retries)
       ["Max retry count shouldn't be negative" (u/wrap-error :negative-max-retries max-retries)]

       (not (int? max-retries))
       ["Max retry count should be an integer" (u/wrap-error :non-int-max-retries max-retries)]

       (not (qualified-symbol? error-handler-fn-sym))
       ["Error handler should be qualified" (u/wrap-error :unqualified-error-handler error-handler-fn-sym)]

       (not (resolve error-handler-fn-sym))
       ["Error handler should be resolvable" (u/wrap-error :unresolvable-error-handler error-handler-fn-sym)]

       (not (some #{2} (u/arities error-handler-fn-sym)))
       ["Error handler should have arity of 2 args" (u/wrap-error :incorrect-arity-error-handler error-handler-fn-sym)]

       (not (qualified-symbol? death-handler-fn-sym))
       ["Death handler should be qualified" (u/wrap-error :unqualified-death-handler death-handler-fn-sym)]

       (not (resolve death-handler-fn-sym))
       ["Death handler should be resolvable" (u/wrap-error :unresolvable-death-handler death-handler-fn-sym)]

       (not (some #{2} (u/arities death-handler-fn-sym)))
       ["Death handler should have arity of 2 args" (u/wrap-error :incorrect-arity-death-handler death-handler-fn-sym)]

       (not (qualified-symbol? retry-delay-sec-fn-sym))
       ["Retry delay sec should be qualified" (u/wrap-error :unqualified-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (resolve retry-delay-sec-fn-sym))
       ["Retry delay sec should be resolvable" (u/wrap-error :unresolvable-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (pos-int? ((u/require-resolve retry-delay-sec-fn-sym) max-retries)))
       ["Retry delay sec should return a positive integer" (u/wrap-error :invalid-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (boolean? skip-dead-queue))
       ["skip-dead-queue should be a boolean" (u/wrap-error :non-boolean-skip-dead-queue skip-dead-queue)])]
    (throw (apply ex-info validation-error))))
