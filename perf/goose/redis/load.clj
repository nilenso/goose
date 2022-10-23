(ns ^:no-doc goose.redis.load
  (:require
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.client :as c]
    [goose.core :as core]
    [goose.specs :as specs]
    [goose.utils :as u]
    [goose.worker :as w]

    [taoensso.carmine :as car])
  (:import
    [eu.rekawek.toxiproxy ToxiproxyClient]
    [eu.rekawek.toxiproxy.model ToxicDirection]))

;;; ========== Redis & Proxy URL ==========
(def redis-producer (redis/new-producer redis/default-opts))
(def redis-proxy "localhost:6380")
(def redis-consumer-url (str "redis://" redis-proxy))
(def redis-consumer (redis/new-consumer {:url redis-consumer-url} 5))

;;; ========== Client Opts ==========
(def client-opts (assoc core/client-opts :broker redis-producer))

(defn bulk-enqueue
  [count]
  (println "[Redis perf-test] Enqueuing:" count "jobs.")
  (let [start-time (u/epoch-time-ms)]
    (dotimes [n count]
      (c/perform-async client-opts `core/my-fn n))
    (println "Jobs enqueued:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))))

(defn dequeue
  [count]
  (let [worker-opts (assoc core/worker-opts :broker redis-consumer)
        start-time (u/epoch-time-ms)
        worker (w/start worker-opts)]
    (while (not= 0 (enqueued-jobs/size redis-producer core/queue))
      (Thread/sleep 200))
    (println "Jobs processed:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))

    (c/perform-async client-opts `core/latency (u/epoch-time-ms))
    (w/stop worker)))

(defn- flush-redis []
  (redis-cmds/wcar* (:redis-conn redis-producer) (car/flushdb "sync")))

(defn enqueue-dequeue
  [count]
  (specs/instrument)
  (flush-redis)

  (bulk-enqueue count)
  (dequeue count))

(defn- add-latency-to-redis
  []
  (let [toxiproxy-client (ToxiproxyClient.)
        proxy (.createProxy toxiproxy-client "redis" redis-proxy "localhost:6379")]
    (.latency (.toxics proxy) "1-ms" (ToxicDirection/DOWNSTREAM) 1)
    proxy))

(defn benchmark
  [_]
  (core/run-benchmarks add-latency-to-redis enqueue-dequeue (* 100 1000)))
