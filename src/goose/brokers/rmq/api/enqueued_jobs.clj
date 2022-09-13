(ns goose.brokers.rmq.api.enqueued-jobs
  {:no-doc true}
  (:require
    [goose.defaults :as d]

    [langohr.queue :as lq]))

(defn size [ch queue]
  (lq/message-count ch (d/prefix-queue queue)))

(defn purge [ch queue]
  (lq/purge ch (d/prefix-queue queue)))
