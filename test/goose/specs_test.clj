(ns goose.specs-test
  (:require
    [goose.batch :as batch]
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.rmq.broker :as rmq]
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]
    [goose.specs :as specs]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is are]]
    [tech.v3.dataset :as ds])
  (:import
    (clojure.lang ExceptionInfo)
    (java.time Instant)))

(defn single-arity-fn [_] "dummy")
(def now (Instant/now))

;;; A tech.v3.DS was reported to have mis-matching types post
;;; de/serialization. This ugliness is the ONLY way to reproduce this bug.
(def tech-v3-dataset-sample
  (assoc (-> {:1 1 :2 2 :3 3 :4 4 :5 5 :6 6 :7 7 :8 8}
             ds/->dataset
             ds/mapseq-reader
             first)
    :9 9))

(deftest specs-test
  (specs/instrument)
  (are [sut]
    (is
      (thrown-with-msg?
        ExceptionInfo
        #"Call to goose.* did not conform to spec."
        (sut)))

    ;; Client specs
    ;; :execute-fn-sym
    #(c/perform-async tu/redis-client-opts 'my-fn)
    #(c/perform-async tu/redis-client-opts `my-fn)
    #(c/perform-async tu/redis-client-opts `tu/redis-client-opts)

    ;; :args
    #(c/perform-async tu/redis-client-opts `tu/my-fn tech-v3-dataset-sample)

    ;; :sec
    #(c/perform-in-sec tu/redis-client-opts 0.2 `tu/my-fn)

    ;; :instant
    #(c/perform-at tu/redis-client-opts "22-July-2022" `tu/my-fn)

    ;; :cron-opts
    (let [cron-opts {:cron-name "my-cron" :cron-schedule "* * * * *"}]
      #(c/perform-every tu/redis-client-opts (assoc cron-opts :cron-name :invalid) `tu/my-fn)
      #(c/perform-every tu/redis-client-opts (assoc cron-opts :cron-schedule "invalid") `tu/my-fn)
      #(c/perform-every tu/redis-client-opts (assoc cron-opts :timezone "invalid-zone-id") `tu/my-fn))

    ;; :perform-batch
    #(c/perform-batch tu/redis-client-opts batch/default-opts `unresolvable-fn (map list [1 2]))
    #(c/perform-batch tu/redis-client-opts batch/default-opts `single-arity-fn {1 2})
    #(c/perform-batch tu/redis-client-opts batch/default-opts `single-arity-fn [1 2])

    ;;  :batch-opts
    #(c/perform-batch tu/redis-client-opts
                      {:linger-sec 1 :callback-fn-sym `unresolvable-fn}
                      `single-arity-fn (map list [1 2]))
    #(c/perform-batch tu/redis-client-opts
                      {:linger-sec "100" :callback-fn-sym `batch/default-callback}
                      `single-arity-fn (map list [1 2]))

    ;; Common specs
    ;; :broker
    #(c/perform-async (assoc tu/redis-client-opts :broker :invalid) `tu/my-fn)

    ;; :queue
    #(c/perform-async (assoc tu/redis-client-opts :queue :non-string) `tu/my-fn)
    #(c/perform-async (assoc tu/redis-client-opts :queue (str (range 300))) `tu/my-fn)
    #(c/perform-at (assoc tu/redis-client-opts :queue d/schedule-queue) now `tu/my-fn)
    #(c/perform-in-sec (assoc tu/redis-client-opts :queue d/dead-queue) 1 `tu/my-fn)
    #(c/perform-async (assoc tu/redis-client-opts :queue d/cron-queue) `tu/my-fn)
    #(c/perform-async (assoc tu/redis-client-opts :queue d/cron-entries) `tu/my-fn)
    #(c/perform-async (assoc tu/redis-client-opts :queue (str d/queue-prefix "olttwa")) `tu/my-fn)

    ;; :retry-opts
    #(c/perform-async (assoc-in tu/redis-client-opts [:retry-opts :max-retries] -1) `tu/my-fn)
    #(c/perform-at (assoc-in tu/redis-client-opts [:retry-opts :retry-delay-sec-fn-sym] 'non-fn-sym) now `tu/my-fn)
    #(c/perform-at (assoc-in tu/redis-client-opts [:retry-opts :retry-queue] :invalid) now `tu/my-fn)
    #(c/perform-in-sec (assoc-in tu/redis-client-opts [:retry-opts :error-handler-fn-sym] `single-arity-fn) 1 `tu/my-fn)
    #(c/perform-async (assoc-in tu/redis-client-opts [:retry-opts :death-handler-fn-sym] `single-arity-fn) `tu/my-fn)
    #(c/perform-in-sec (assoc-in tu/redis-client-opts [:retry-opts :skip-dead-queue] 1) 1 `tu/my-fn)
    #(c/perform-async (assoc-in tu/redis-client-opts [:retry-opts :extra-key] :foo-bar) `tu/my-fn)

    ;; Tests for functions that have manual specs assertion.

    ;; :redis-opts
    #(redis/new-producer (assoc redis/default-opts :url :invalid-url))
    #(redis/new-consumer (assoc redis/default-opts :pool-opts :invalid-pool-opts))
    #(redis/new-consumer redis/default-opts 61)

    ;; rmq-broker :settings
    #(rmq/new-consumer {:settings :invalid})

    ;; rmq-broker :queue-type
    #(rmq/new-producer (assoc rmq/default-opts :queue-type {:type :invalid}))
    #(rmq/new-consumer (assoc rmq/default-opts :queue-type {:type d/rmq-quorum-queue :replication-factor 0}))

    ;; rmq-broker :publisher-confirms
    #(rmq/new-producer (assoc rmq/default-opts :publisher-confirms {:strategy :invalid}))
    #(rmq/new-consumer (assoc rmq/default-opts :publisher-confirms {:strategy d/sync-confirms :timeout-ms 0}))
    #(rmq/new-producer (assoc rmq/default-opts :publisher-confirms {:strategy d/sync-confirms :timeout-ms 10 :max-retries -1}))
    #(rmq/new-consumer (assoc rmq/default-opts :publisher-confirms {:strategy d/sync-confirms :timeout-ms 10 :retry-delay-ms 0}))
    #(rmq/new-producer (assoc rmq/default-opts :publisher-confirms {:strategy d/async-confirms :ack-handler 'invalid}))
    #(rmq/new-consumer (assoc rmq/default-opts :publisher-confirms {:strategy d/async-confirms :nack-handler `tu/my-fn}))

    ;; rmq-broker :return-listener
    #(rmq/new-producer (assoc rmq/default-opts :return-listener :non-fn))

    ;; rmq-broker :shutdown-listener
    #(rmq/new-consumer (assoc rmq/default-opts :shutdown-listener :non-fn))

    ;; rmq-producer channel-pool-size
    #(rmq/new-producer rmq/default-opts -1)

    ;; Worker specs
    #(w/start (assoc tu/redis-worker-opts :threads -1.1))
    #(w/start (assoc tu/redis-worker-opts :queue (str (range 300))))
    #(w/start (assoc tu/rmq-worker-opts :graceful-shutdown-sec -2))
    #(w/start (assoc tu/redis-worker-opts :metrics-plugin :invalid))
    #(w/start (assoc tu/rmq-worker-opts :middlewares "non-fn"))

    ;; :statsd-opts
    #(statsd/new (assoc statsd/default-opts :enabled? 1))
    #(statsd/new (assoc statsd/default-opts :host 127.0))
    #(statsd/new (assoc statsd/default-opts :port "8125"))
    #(statsd/new (assoc statsd/default-opts :prefix :symbol))
    #(statsd/new (assoc statsd/default-opts :sample-rate 1))
    #(statsd/new (assoc statsd/default-opts :tags '("service:maverick")))))
