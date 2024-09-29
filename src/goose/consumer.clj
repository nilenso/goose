(ns ^:no-doc goose.consumer
  (:require
   [goose.utils :as u]))

(defn execute-job
  [_opts {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args))
