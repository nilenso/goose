(ns goose.rmq.load
  (:require
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.rmq.broker :as rmq]
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.brokers.rmq.return-listener :as return-listener]
    [goose.brokers.rmq.shutdown-listener :as shutdown-listener]
    [goose.client :as c]
    [goose.core :as core]
    [goose.defaults :as d]
    [goose.specs :as specs]
    [goose.worker :as w]
    [goose.utils :as u]

    [clojure.tools.logging :as log]
    [langohr.queue :as lq])
  (:import
    [eu.rekawek.toxiproxy ToxiproxyClient]
    [eu.rekawek.toxiproxy.model ToxicDirection]))

(defn dummy-ack-handler [_ _])
(defn nack-handler [delivery-tag multiple]
  (log/error (format "Negative-ACK delivery-tag: %d multiple: %s" delivery-tag multiple)))

;;; ========== RabbitMQ & Proxy URL ==========
(def rmq-host "localhost:5672")
(def rmq-producer-url (str "amqp://guest:guest@" rmq-host))
(def rmq-proxy "localhost:5673")
(def rmq-consumer-url (str "amqp://guest:guest@" rmq-proxy))

;;; ========== Publisher Confirms ==========
(def async-strategy
  {:strategy     d/async-confirms
   :ack-handler  dummy-ack-handler
   :nack-handler nack-handler})
(def sync-strategy rmq-publisher-confirms/sync)

;;; ========== Producer Opts ==========
(def rmq-producer-opts
  {:settings           {:uri rmq-producer-url}
   :queue-type         rmq-queue/classic
   :publisher-confirms async-strategy
   :return-listener    return-listener/default
   :shutdown-listener  shutdown-listener/default})
(def rmq-producer (rmq/new-producer rmq-producer-opts 25))

;;; ========== Client Opts ==========
(def client-opts (assoc core/client-opts :broker rmq-producer))

(defn bulk-enqueue
  [count]
  (println "[RabbitMQ perf-test] Enqueuing:" count "jobs.")
  (let [start-time (u/epoch-time-ms)]
    (dotimes [n count]
      (c/perform-async client-opts `core/my-fn n))

    (println "Jobs enqueued:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))))

(defn dequeue
  [count]
  (let [rmq-consumer-opts (assoc-in rmq-producer-opts [:settings :uri] rmq-consumer-url)
        rmq-consumer (rmq/new-consumer rmq-consumer-opts)
        worker-opts (assoc core/worker-opts :broker rmq-consumer)
        start-time (u/epoch-time-ms)
        worker (w/start worker-opts)]
    (while (not= 0 (enqueued-jobs/size rmq-producer core/queue))
      (Thread/sleep 200))
    (println "Jobs processed:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))

    (c/perform-async client-opts `core/latency (u/epoch-time-ms))
    (w/stop worker)))

(defn- purge-rmq []
  (let [ch (u/random-element (:channels rmq-producer))
        ready-queue (d/prefix-queue core/queue)
        queue-opts (assoc (:queue-type rmq-producer-opts) :queue ready-queue)]
    (rmq-queue/declare ch queue-opts)
    (lq/purge ch ready-queue)))

(defn enqueue-dequeue
  [count]
  (specs/instrument)
  (rmq-queue/clear-cache)
  (purge-rmq)

  (bulk-enqueue count)

  (dequeue count))

(defn- add-latency-to-rmq
  []
  (let [toxiproxy-client (ToxiproxyClient.)
        proxy (.createProxy toxiproxy-client "rmq" rmq-proxy rmq-host)]
    (.latency (.toxics proxy) "1-ms" (ToxicDirection/DOWNSTREAM) 1)
    proxy))

(defn benchmark
  [_]
  (core/run-benchmarks add-latency-to-rmq enqueue-dequeue (* 100 1000)))
