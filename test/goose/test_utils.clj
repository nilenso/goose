(ns goose.test-utils
  (:require
    [goose.api.api :as api]
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.statsd :as statsd]
    [goose.specs :as specs]

    [taoensso.carmine :as car]))

(defn my-fn [arg] arg)

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))
(def broker-opts
  {:redis
   {:url       redis-url
    :pool-opts {:max-total-per-key (inc d/internal-thread-pool-size)}}})
(def redis-conn (r/conn (:redis broker-opts)))
(defn clear-redis [] (r/wcar* redis-conn (car/flushdb "SYNC")))

(defn fixture
  [f]
  (specs/instrument)
  (clear-redis)
  (api/initialize broker-opts)

  (f)

  (clear-redis))

(def queue "test")
(def client-opts
  {:broker-opts broker-opts
   :queue       queue
   :retry-opts  retry/default-opts})

(def worker-opts
  {:threads                        1
   :broker-opts                    broker-opts
   :queue                          queue
   :graceful-shutdown-sec          1
   :scheduler-polling-interval-sec 1
   :statsd-opts                    (assoc statsd/default-opts :tags {:env "test"})})
