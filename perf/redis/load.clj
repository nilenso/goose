(ns redis.load
  (:require
    [goose.client :as c]
    [goose.worker :as w]
    [goose.utils :as u]
    [goose.redis :as r]
    [goose.brokers.redis :as redis-broker]
    [goose.defaults :as d]

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

(defn dummy-handler [_ _])

(def client-opts
  (assoc c/default-opts
    :broker-opts {:redis redis-broker/default-opts}
    :retry-opts {:max-retries          1
                 :skip-dead-queue      true
                 :error-handler-fn-sym `dummy-handler
                 :death-handler-fn-sym `dummy-handler}))

(def redis-conn
  (r/conn {:url       d/default-redis-url
           :pool-opts {:max-total-per-key 1
                       :max-idle-per-key  1}}))
(def redis-proxy "localhost:6380")

(defn- flush-redis []
  (r/wcar* redis-conn (car/flushdb "sync")))

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
  (let [start-time (u/epoch-time-ms)]
    (dotimes [n count]
      (c/perform-async client-opts `my-fn n))
    (println "Jobs enqueued:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))))

(defn dequeue
  [count]
  (let [worker-opts (assoc w/default-opts
                      :broker-opts {:redis {:url (str "redis://" redis-proxy)}}
                      :threads 25)
        start-time (u/epoch-time-ms)
        worker (w/start worker-opts)]
    (while (not (= 0 (r/wcar* redis-conn (car/llen (d/prefix-queue d/default-queue)))))
      (Thread/sleep 200))
    (println "Jobs processed:" count "Milliseconds taken:" (- (u/epoch-time-ms) start-time))

    (c/perform-async client-opts `latency (u/epoch-time-ms))
    (w/stop worker)))

(defn enqueue-dequeue
  [count]
  (flush-redis)

  (bulk-enqueue count)
  (dequeue count)

  (flush-redis))

(defn benchmark
  [_]
  (println "====================================
Benchmark runs beyond 60 minutes.
We're using criterium to warm-up JVM & pre-run GC.
Exit using ctrl-C after recording ~10 samples.
====================================")
  (let [toxiproxy (add-latency-to-redis)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (reset toxiproxy))))
    (criterium/bench (enqueue-dequeue (* 100 1000)))
    (reset toxiproxy)))
