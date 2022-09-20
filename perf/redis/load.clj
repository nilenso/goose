(ns redis.load
  {:no-doc true}
  (:require
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]
    [goose.utils :as u]
    [goose.worker :as w]

    [criterium.core :as criterium]
    [taoensso.carmine :as car])
  (:import
    [eu.rekawek.toxiproxy ToxiproxyClient]
    [eu.rekawek.toxiproxy.model ToxicDirection]))

(defn my-fn [arg]
  (when (= 0 (mod arg 100))
    (throw (ex-info "1% jobs fail" {}))))

(defn latency [enqueue-time]
  (println "Latency:" (- (u/epoch-time-ms) enqueue-time) "ms"))

(defn dummy-error-handler [_ _ _])

(def redis-broker (redis/new redis/default-opts))
(def redis-proxy "localhost:6380")
(def redis-broker-with-latency
  (redis/new
    {:url                            (str "redis://" redis-proxy)
     :scheduler-polling-interval-sec 10}
    25))

(defn retry-delay-sec [_] 1)
(def client-opts
  (assoc c/default-opts
    :broker redis-broker
    :retry-opts {:max-retries            1
                 :skip-dead-queue        true
                 :error-handler-fn-sym   `dummy-error-handler
                 :death-handler-fn-sym   `dummy-error-handler
                 :retry-delay-sec-fn-sym `retry-delay-sec}))

(defn- flush-redis []
  (redis-cmds/wcar* redis-broker (car/flushdb "sync")))

(defprotocol Toxiproxy
  (reset [_]))

(defn- add-latency-to-redis
  []
  (let [toxiproxy-client (ToxiproxyClient.)
        proxy (.createProxy toxiproxy-client "redis" redis-proxy "localhost:6379")]
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
  (let [worker-opts (assoc w/default-opts
                      :broker redis-broker-with-latency
                      :threads 25
                      :metrics-plugin (statsd/new {:enabled? false}))
        start-time (u/epoch-time-ms)
        worker (w/start worker-opts)]
    (while (not (= 0 (enqueued-jobs/size redis-broker d/default-queue)))
      (Thread/sleep 200))
    (println "Jobs processed:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))

    (c/perform-async client-opts `latency (u/epoch-time-ms))
    (w/stop worker)))

(defn enqueue-dequeue
  [count]
  (flush-redis)

  (bulk-enqueue count)
  (dequeue count))

(defn shutdown-fn
  [toxiproxy]
  (reset toxiproxy)
  (flush-redis))

(defn benchmark
  [_]
  (println
    "====================================
    Benchmark runs beyond 60 minutes.
    We're using criterium to warm-up JVM & pre-run GC.
    Exit using ctrl-C after recording ~10 samples.
    ====================================")
  (let [toxiproxy (add-latency-to-redis)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(shutdown-fn toxiproxy)))
    (criterium/bench (enqueue-dequeue (* 100 1000)))
    (shutdown-fn toxiproxy)))
