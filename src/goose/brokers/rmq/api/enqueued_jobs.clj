(ns goose.brokers.rmq.api.enqueued-jobs
  {:no-doc true}
  (:require [langohr.queue :as lq]
            [goose.defaults :as d]))

(defn size [ch queue]
  (lq/message-count ch (d/prefix-queue queue)))

(defn purge [ch queue]
  (lq/purge ch (d/prefix-queue queue)))
