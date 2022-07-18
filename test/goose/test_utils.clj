(ns goose.test-utils
  (:require
    [goose.api.api :as api]
    [goose.statsd :as statsd]
    [goose.redis :as r]

    [taoensso.carmine :as car]))

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))
(def broker-opts {:redis {:redis-url redis-url}})
(def redis-conn (r/conn (:redis broker-opts)))
(defn clear-redis [] (r/wcar* redis-conn (car/flushdb "SYNC")))

(defn fixture
  [f]
  (clear-redis)
  (api/initialize broker-opts)
  (f))

(defn worker-opts
  [queue]
  {:threads                        1
   :broker-opts                    broker-opts
   :queue                          queue
   :graceful-shutdown-sec          1
   :scheduler-polling-interval-sec 1
   :statsd-opts                    (assoc statsd/default-opts :tags {:env "test"})})
