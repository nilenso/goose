(ns goose.brokers.rmq.api.enqueued-jobs
  ^:no-doc
  (:require
    [goose.defaults :as d]

    [langohr.queue :as lq]))

(defn size [ch queue]
  (let [ready-queue (d/prefix-queue queue)]
    (lq/message-count ch ready-queue)))

(defn purge [ch queue]
  (let [ready-queue (d/prefix-queue queue)]
    (< 0 (:message-count (lq/purge ch ready-queue)))))
