(ns goose.test-utils
  (:require
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.rmq.broker :as rmq]
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.brokers.rmq.return-listener :as return-listener]
    [goose.brokers.rmq.shutdown-listener :as shutdown-listener]
    [goose.defaults :as d]
    [goose.retry :as retry]
    [goose.specs :as specs]
    [goose.metrics.statsd :as statsd]
    [goose.utils :as u]

    [langohr.queue :as lq]
    [taoensso.carmine :as car]))

(defn my-fn [arg & _] arg)
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
(def redis-opts {:url redis-url})
(def redis-conn {:spec {:uri (:url redis-opts)}})
(def redis-producer (redis/new-producer redis-opts))
(def redis-consumer (redis/new-consumer redis-opts 1))
(def redis-client-opts (assoc client-opts :broker redis-producer))
(def redis-worker-opts (assoc worker-opts :broker redis-consumer))
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
(def rmq-opts
  {:settings             {:uri rmq-url}
   :queue-type           rmq-queue/classic
   :publisher-confirms   rmq-publisher-confirms/sync
   :return-listener-fn   return-listener/default
   :shutdown-listener-fn shutdown-listener/default})
(def rmq-producer (rmq/new-producer rmq-opts 1))
(def rmq-consumer (rmq/new-consumer rmq-opts))
(def rmq-client-opts (assoc client-opts :broker rmq-producer))
(def rmq-worker-opts (assoc worker-opts :broker rmq-consumer))
(defn rmq-delete-test-queues []
  (let [ch (u/random-element (:channels rmq-producer))]
    (lq/delete ch (d/prefix-queue queue))
    (lq/delete ch (d/prefix-queue "quorum-test"))
    (lq/delete ch (d/prefix-queue "test-retry"))
    (lq/delete ch (d/prefix-queue "sync-publisher-confirms-test"))
    (lq/delete ch (d/prefix-queue "async-publisher-confirms-test"))
    (lq/delete ch d/prefixed-dead-queue)))

(defn rmq-fixture
  [f]
  (specs/instrument)
  (rmq-delete-test-queues)
  (rmq-queue/clear-cache)

  (f)

  (rmq-delete-test-queues))

(defn exit-cli
  "A utility function called by test-runner.
  Contains logic necessary to exit CLI.
  Not necessary to exit REPL."
  []
  (rmq/close rmq-producer)
  (rmq/close rmq-consumer)

  ; clj-statsd uses agents.
  ; If not shutdown, program won't quit.
  ; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))
