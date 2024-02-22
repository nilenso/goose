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
    [goose.metrics.statsd :as statsd]
    [goose.retry :as retry]
    [goose.specs :as specs]
    [goose.utils :as u]

    [langohr.queue :as lq]
    [taoensso.carmine :as car]))

(defn my-fn [arg & _]
  arg)
(def queue "test")
(defn no-op-error-handler [_ _ _])
(def retry-opts (assoc retry/default-opts
                  :error-handler-fn-sym `no-op-error-handler
                  :death-handler-fn-sym `no-op-error-handler))
(def client-opts
  {:queue      queue
   :retry-opts retry-opts})
(def worker-opts
  {:threads               1
   :queue                 queue
   :graceful-shutdown-sec 1
   :metrics-plugin        (statsd/new (assoc statsd/default-opts :tags {:env "test"}))})

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))
(def redis-producer-opts {:url redis-url})
(def redis-consumer-opts {:url redis-url :scheduler-polling-interval-sec 1})
(def redis-conn {:spec {:uri (:url redis-producer-opts)}})
(def redis-producer (redis/new-producer redis-producer-opts))
(def redis-consumer (redis/new-consumer redis-consumer-opts 1))
(def redis-client-opts (assoc client-opts :broker redis-producer))
(def redis-worker-opts (assoc worker-opts :broker redis-consumer))
(defn clear-redis []
  (redis-cmds/wcar* redis-conn (car/flushdb "SYNC")))

(defn redis-fixture
  [f]
  (specs/instrument)
  (clear-redis)

  (f)

  (clear-redis))

(def called? (atom false))
(defn get-called? [] @called?)
(defn set-called? [value] (reset! called? value))

(defn reset-stubs
  [f]
  (set-called? false)
  (f))

(defn stub [f]
  (fn [& args]
    (set-called? true)
    (apply f args)))

;; RMQ ---------
(def rmq-url
  (let [host (or (System/getenv "GOOSE_TEST_RABBITMQ_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_RABBITMQ_PORT") "5672")
        username (or (System/getenv "GOOSE_TEST_RABBITMQ_USERNAME") "guest")
        password (or (System/getenv "GOOSE_TEST_RABBITMQ_PASSWORD") "guest")]
    (str "amqp://" username ":" password "@" host ":" port)))
(def rmq-opts
  {:settings           {:uri rmq-url}
   :queue-type         rmq-queue/classic
   :publisher-confirms rmq-publisher-confirms/sync
   :return-listener    return-listener/default
   :shutdown-listener  shutdown-listener/default})
(def rmq-producer (rmq/new-producer rmq-opts 1))
(def rmq-consumer (rmq/new-consumer rmq-opts))
(def rmq-client-opts (assoc client-opts :broker rmq-producer))
(def rmq-worker-opts (assoc worker-opts :broker rmq-consumer))
(defn rmq-delete-test-queues
  []
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

  ;; clj-statsd uses agents.
  ;; If not shutdown, program won't quit.
  ;; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))

;; ref: https://stackoverflow.com/questions/6694530/executing-a-function-with-a-timeout/27550676#answer-27550676
(defn timeout
  [timeout-ms callback]
  (let [fut (future (callback))
        ret (deref fut timeout-ms :timed-out)]
    (when (= ret :timed-out)
      (future-cancel fut))
    ret))

(defmacro with-timeout
  [timeout-ms & body]
  `(timeout ~timeout-ms (fn [] ~@body)))
