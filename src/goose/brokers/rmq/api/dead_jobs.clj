(ns goose.brokers.rmq.api.dead-jobs
  {:no-doc true}
  (:require [langohr.queue :as lq]
            [goose.defaults :as d]))

(defn size [ch]
  (lq/message-count ch d/prefixed-dead-queue))

(defn purge [ch]
  (println (lq/purge ch d/prefixed-dead-queue)))
