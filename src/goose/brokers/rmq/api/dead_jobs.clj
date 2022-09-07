(ns goose.brokers.rmq.api.dead-jobs
  {:no-doc true}
  (:refer-clojure :exclude [pop])
  (:require
    [goose.defaults :as d]

    [langohr.basic :as lb]
    [langohr.queue :as lq]
    [taoensso.nippy :as nippy]))

(defn size [ch]
  (lq/message-count ch d/prefixed-dead-queue))

(defn pop [ch]
  (let [[_ payload] (lb/get ch d/prefixed-dead-queue)]
    (nippy/thaw payload)))

(defn purge [ch]
  (< 0 (:message-count (lq/purge ch d/prefixed-dead-queue))))
