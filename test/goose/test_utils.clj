(ns goose.test-utils
  (:require
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.rmq.broker :as rmq]
    [goose.brokers.rmq.channel :as channels]
    [goose.defaults :as d]
    [goose.retry :as retry]
    [goose.specs :as specs]
    [goose.metrics.statsd :as statsd]

    [langohr.queue :as lq]
    [taoensso.carmine :as car]
    [goose.utils :as u]))

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

(def rmq-url
  (let [host (or (System/getenv "GOOSE_TEST_RABBITMQ_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_RABBITMQ_PORT") "5672")
        username (or (System/getenv "GOOSE_TEST_RABBITMQ_USERNAME") "guest")
        password (or (System/getenv "GOOSE_TEST_RABBITMQ_PASSWORD") "guest")]
    (str "amqp://" username ":" password "@" host ":" port)))
(def rmq-opts {:settings {:uri rmq-url}})
(def client-rmq-broker (rmq/new rmq-opts 1))
(def worker-rmq-broker (rmq/new rmq-opts))
(def rmq-client-opts (assoc client-opts :broker client-rmq-broker))
(def rmq-worker-opts (assoc worker-opts :broker worker-rmq-broker))
(defn rmq-purge-test-queue []
  (let [ch (u/get-one (:channels client-rmq-broker))]
    (lq/purge ch (d/prefix-queue queue))))

(defn rmq-fixture
  [f]
  (specs/instrument)

  (f)

  (rmq-purge-test-queue))

(defn exit-cli
  "A utility function called by test-runner.
  Contains logic necessary to exit CLI.
  Not necessary to exit REPL."
  []
  (rmq/close client-rmq-broker)
  (rmq/close worker-rmq-broker)

  ; clj-statsd uses agents.
  ; If not shutdown, program won't quit.
  ; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))
