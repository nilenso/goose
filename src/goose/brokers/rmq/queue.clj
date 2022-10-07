(ns goose.brokers.rmq.queue
  (:require
    [goose.defaults :as d]))

(def classic
  {:type d/rmq-classic-queue})

(def quorum
  {:type               d/rmq-quorum-queue
   :replication-factor 5})

(defn ^:no-doc arguments
  [{:keys [type replication-factor]}]
  (condp = type
    d/rmq-classic-queue
    {"x-queue-type"   d/rmq-classic-queue
     "x-max-priority" d/rmq-high-priority}

    d/rmq-quorum-queue
    {"x-queue-type"                d/rmq-quorum-queue
     "x-quorum-initial-group-size" replication-factor}))
