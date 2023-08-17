(ns goose.brokers.rmq.queue
  (:refer-clojure :exclude [declare])
  (:require
    [goose.defaults :as d]

    [langohr.exchange :as lex]
    [langohr.queue :as lq]))

(def classic
  {:type d/rmq-classic-queue})

(def quorum
  {:type               d/rmq-quorum-queue
   :replication-factor d/rmq-replication-factor})

(defn- arguments
  [{:keys [type replication-factor]}]
  (condp = type
    d/rmq-classic-queue
    {"x-queue-type"   d/rmq-classic-queue
     "x-max-priority" d/rmq-high-priority}

    d/rmq-quorum-queue
    {"x-queue-type"                d/rmq-quorum-queue
     "x-quorum-initial-group-size" replication-factor}))

(def ^:private declared-queues (atom #{}))
(defn ^:no-doc clear-cache [] (reset! declared-queues #{}))

(defn ^:no-doc declare
  [ch {:keys [queue] :as queue-opts}]
  (when-not (@declared-queues queue)
    (lex/declare ch
                 d/rmq-delay-exchange
                 d/rmq-delay-exchange-type
                 {:durable     true
                  :auto-delete false
                  :arguments   {"x-delayed-type" "direct"}})

    (let [arguments (arguments queue-opts)]
      (lq/declare ch
                  queue
                  {:durable     true
                   :auto-delete false
                   :exclusive   false
                   :arguments   arguments}))
    (lq/bind ch queue d/rmq-delay-exchange {:routing-key queue})
    (swap! declared-queues conj queue)))
