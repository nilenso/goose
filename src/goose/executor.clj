(ns goose.executor
  {:no-doc true}
  (:require
    [goose.utils :as u]))

(defn execute-job
  [_ {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args))
