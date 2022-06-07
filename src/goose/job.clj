(ns goose.job
  (:require
    [goose.utils :as u]
    [goose.redis :as r]))

(defn new
  [execute-fn-sym args queue retry-opts]
  {:id             (str (random-uuid))
   :execute-fn-sym execute-fn-sym
   :args           args
   :queue          queue
   :retry-opts     retry-opts
   :enqueued-at    (u/epoch-time-ms)})

(defn enqueue
  [redis-conn
   {:keys [queue] :as job}]
  (r/enqueue-back redis-conn queue job))
