(ns goose.validations.retry
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]
    [goose.validations.queue :refer [validate-queue]]

    [clojure.string :as str]))

(defn validate-retry-opts
  [{:keys [max-retries retry-delay-sec-fn-sym
           retry-queue skip-dead-queue
           error-handler-fn-sym death-handler-fn-sym]
    :as   retry-opts}]
  (when retry-queue (validate-queue retry-queue))
  (when-let
    [validation-error
     (cond
       (not-empty (apply dissoc retry-opts [:max-retries :retry-delay-sec-fn-sym :skip-dead-queue
                                            :retry-queue :error-handler-fn-sym :death-handler-fn-sym]))
       [":retry-opts shouldn't have any extra keys" (u/wrap-error :retry-opts-invalid retry-opts)]

       (neg? max-retries)
       [":max-retries count shouldn't be negative" (u/wrap-error :negative-max-retries max-retries)]

       (not (int? max-retries))
       [":max-retries count should be an integer" (u/wrap-error :non-int-max-retries max-retries)]

       (when retry-queue (not (str/starts-with? retry-queue d/queue-prefix)))
       [":retry-queue should be prefixed" (u/wrap-error :unprefixed-retry-queue retry-queue)]

       (not (qualified-symbol? error-handler-fn-sym))
       [":error-handler-fn-sym should be qualified" (u/wrap-error :unqualified-error-handler error-handler-fn-sym)]

       (not (resolve error-handler-fn-sym))
       [":error-handler-fn-sym should be resolvable" (u/wrap-error :unresolvable-error-handler error-handler-fn-sym)]

       (not (some #{2} (u/arities error-handler-fn-sym)))
       [":error-handler-fn-sym should have arity of 2 args" (u/wrap-error :incorrect-arity-error-handler error-handler-fn-sym)]

       (not (qualified-symbol? death-handler-fn-sym))
       [":death-handler-fn-sym should be qualified" (u/wrap-error :unqualified-death-handler death-handler-fn-sym)]

       (not (resolve death-handler-fn-sym))
       [":death-handler-fn-sym should be resolvable" (u/wrap-error :unresolvable-death-handler death-handler-fn-sym)]

       (not (some #{2} (u/arities death-handler-fn-sym)))
       [":death-handler-fn-sym should have arity of 2 args" (u/wrap-error :incorrect-arity-death-handler death-handler-fn-sym)]

       (not (qualified-symbol? retry-delay-sec-fn-sym))
       [":retry-delay-sec-fn-sym should be qualified" (u/wrap-error :unqualified-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (resolve retry-delay-sec-fn-sym))
       [":retry-delay-sec-fn-sym should be resolvable" (u/wrap-error :unresolvable-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (pos-int? ((u/require-resolve retry-delay-sec-fn-sym) max-retries)))
       [":retry-delay-sec-fn-sym should return a positive integer" (u/wrap-error :invalid-retry-delay-sec retry-delay-sec-fn-sym)]

       (not (boolean? skip-dead-queue))
       ["skip-dead-queue should be a boolean" (u/wrap-error :non-boolean-skip-dead-queue skip-dead-queue)])]
    (throw (apply ex-info validation-error))))
