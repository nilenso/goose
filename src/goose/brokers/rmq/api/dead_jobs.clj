(ns goose.brokers.rmq.api.dead-jobs
  ^:no-doc
  (:refer-clojure :exclude [pop])
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.basic :as lb]
    [langohr.queue :as lq]))

(defn size [ch]
  (lq/message-count ch d/prefixed-dead-queue))

(defn pop [ch]
  (let [[_ payload] (lb/get ch d/prefixed-dead-queue true)]
    (u/decode payload)))

(defn replay-n-jobs [ch queue-type publisher-confirms n]
  (when (pos-int? n)
    (loop [replayed? (rmq-cmds/replay-dead-job ch queue-type publisher-confirms)
           iterations 1]
      (if (and replayed? (< iterations n))
        (recur (rmq-cmds/replay-dead-job ch queue-type publisher-confirms)
               (inc iterations))
        (if replayed? iterations (dec iterations))))))

(defn purge [ch]
  (< 0 (:message-count (lq/purge ch d/prefixed-dead-queue))))
