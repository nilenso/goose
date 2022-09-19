(ns goose.brokers.rmq.queue
  (:require
    [goose.defaults :as d]))

(def classic
  {:type d/classic-queue})

(def quorum
  {:type               d/quorum-queue
   :replication-factor 5})

(defn ^:no-doc arguments
  [{:keys [type replication-factor]}]
  (condp = type
    d/classic-queue
    {"x-max-priority" d/rmq-high-priority}

    d/quorum-queue
    {"x-queue-type"                d/quorum-queue
     "x-quorum-initial-group-size" replication-factor}))
