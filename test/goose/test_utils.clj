(ns goose.test-utils
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.retry :as retry]
    [goose.statsd :as statsd]
    [goose.specs :as specs]

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
   :statsd-opts           (assoc statsd/default-opts :tags {:env "test"})})

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))
(def redis-opts {:url redis-url :type d/redis :scheduler-polling-interval-sec 1})
(def redis-conn {:spec {:uri (:url redis-opts)}})
(def redis-client-opts (assoc client-opts :broker-opts redis-opts))
(def redis-worker-opts (assoc worker-opts :broker-opts redis-opts))
(defn clear-redis [] (redis-cmds/wcar* redis-conn (car/flushdb "SYNC")))

(defn fixture
  [f]
  (specs/instrument)
  (clear-redis)

  (f)

  (clear-redis))
