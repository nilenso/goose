(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-error-handler
  [job ex]
  (log/error ex "Job execution failed." job))

(defn default-death-handler
  [job ex]
  (log/error ex "Job retries exhausted." job))

(defn default-retry-delay-sec
  [retry-count]
  ; TODO: fill this
  (* 2 retry-count))

(def default-opts
  {:max-retries            0
   :retry-delay-sec-fn-sym `default-retry-delay-sec
   :retry-queue            nil
   :error-handler-fn-sym   `default-error-handler
   :skip-dead-queue        false
   :death-handler-fn-sym   `default-death-handler})
