(ns goose.test-utils
  (:require
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.retry :as retry]
    [goose.specs :as specs]
    [goose.metrics.statsd :as statsd]

    [taoensso.carmine :as car]))

(defn my-fn [arg] arg)
(def queue "test")
(def client-opts
  {:queue      queue
   :retry-opts retry/default-opts})
(def worker-opts
  {:threads               1
   :queue                 queue
   :graceful-shutdown-sec 1
   :metrics-plugin        (statsd/new (assoc statsd/default-opts :tags {:env "test"}))})

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))
(def redis-opts {:url redis-url :scheduler-polling-interval-sec 1})
(def redis-conn {:spec {:uri (:url redis-opts)}})
(def redis-broker (redis/new redis-opts 1))
(def redis-client-opts (assoc client-opts :broker redis-broker))
(def redis-worker-opts (assoc worker-opts :broker redis-broker))
(defn clear-redis [] (redis-cmds/wcar* redis-conn (car/flushdb "SYNC")))

(defn redis-fixture
  [f]
  (specs/instrument)
  (clear-redis)

  (f)

  (clear-redis))
