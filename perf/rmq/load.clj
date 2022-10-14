(ns rmq.load
  (:require
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]
    [langohr.queue :as lq]
    [goose.utils :as u]
    [goose.defaults :as d]
    [criterium.core :as criterium]
    [goose.client :as c]
    [goose.worker :as w]
    [goose.brokers.rmq.broker :as rmq]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [clojure.tools.logging :as log])
  (:import
    [eu.rekawek.toxiproxy ToxiproxyClient]
    [eu.rekawek.toxiproxy.model ToxicDirection]))


(defn my-fn [arg]
  (when (= 0 (mod arg 100))
    (throw (ex-info "1% jobs fail" {}))))

(defn latency [enqueue-time]
  (println "Latency:" (- (u/epoch-time-ms) enqueue-time) "ms"))

(defn dummy-error-handler [_ _ _])
(defn ack-handler [_ _])
(defn nack-handler [delivery-tag multiple]
  (log/error (format "Negative-ACK delivery-tag: %d multiple: %s" delivery-tag multiple)))

(def queue "load-test")
(def rmq-client-url "amqp://guest:guest@localhost:5672")
(def rmq-proxy "localhost:5673")
(def rmq-worker-url (str "amqp://guest:guest@" rmq-proxy))
(def async-strategy
  {:strategy     rmq-publisher-confirms/async
   :ack-handler  `ack-handler
   :nack-handler `nack-handler})
(def sync-strategy
  {:strategy rmq-publisher-confirms/sync
   :timeout  5000})
(def rmq-opts
  {:publisher-confirms async-strategy})
(def rmq-client-broker
  (rmq/new (assoc rmq-opts :settings {:uri rmq-client-url}) 10))

(defn retry-delay-sec [_] 1)
(def client-opts
  {:queue      queue
   :broker     rmq-client-broker
   :retry-opts {:max-retries            1
                :skip-dead-queue        true
                :retry-delay-sec-fn-sym `retry-delay-sec
                :error-handler-fn-sym   `dummy-error-handler
                :death-handler-fn-sym   `dummy-error-handler}})


(defn- purge-rmq []
  (let [ch (u/random-element (:channels rmq-client-broker))]
    (lq/purge ch (d/prefix-queue queue))))

(defprotocol Toxiproxy
  (reset [_]))

(defn- add-latency-to-rmq
  []
  (let [toxiproxy-client (ToxiproxyClient.)
        proxy (.createProxy toxiproxy-client "rmq" rmq-proxy "localhost:5672")]
    (.latency (.toxics proxy) "1-ms" (ToxicDirection/DOWNSTREAM) 1)
    (reify Toxiproxy
      (reset [_]
        (.delete proxy)))))

(defn bulk-enqueue
  [count]
  (println "Enqueuing:" count "jobs.")
  (let [start-time (u/epoch-time-ms)]
    (dotimes [n count]
      (c/perform-async client-opts `my-fn n))

    (println "Jobs enqueued:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))))

(defn dequeue
  [count]
  (let [rmq-worker-broker (rmq/new (assoc rmq-opts :settings {:uri rmq-worker-url}) 10)
        worker-opts (assoc w/default-opts
                      :queue queue
                      :broker rmq-worker-broker
                      :threads 25)
        start-time (u/epoch-time-ms)
        worker (w/start worker-opts)]
    (while (not= 0 (enqueued-jobs/size rmq-worker-broker queue))
      (Thread/sleep 200))
    (println "Jobs processed:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))

    (c/perform-async client-opts `latency (u/epoch-time-ms))
    (w/stop worker)))

(defn enqueue-dequeue
  [count]
  (purge-rmq)

  (bulk-enqueue count)

  (dequeue count))

(defn shutdown-fn
  [toxiproxy]
  (reset toxiproxy)
  (purge-rmq))

(defn benchmark
  [_]
  (println
    "====================================
    Benchmark runs beyond 60 minutes.
    We're using criterium to warm-up JVM & pre-run GC.
    Exit using ctrl-C after recording ~10 samples.
    ====================================")
  (let [toxiproxy (add-latency-to-rmq)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(shutdown-fn toxiproxy)))

    (criterium/bench (enqueue-dequeue (* 100 1000)))
    (shutdown-fn toxiproxy)))
