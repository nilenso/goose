(ns goose.specs-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [goose.batch :as batch]
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.rmq.broker :as rmq]
    [goose.client :as c]
    [goose.console :as console]
    [goose.defaults :as d]
    [goose.metrics.statsd :as statsd]
    [goose.specs :as specs]
    [goose.test-utils :as tu]
    [goose.utils :as u]

    [goose.worker :as w])
  (:import
    (clojure.lang ExceptionInfo)
    (java.time Instant)
    (java.util HashMap)
    (tech.v3.datatype FastStruct)))

(defn single-arity-fn [_] "dummy")
(def now (Instant/now))

(deftest args-encoding-consistency-test
  ;; Not extending nippy serialization for custom data-types can lead to unexpected bugs.
  ;; For instance, a de/serialized FastStruct object's value will be equal to original.
  ;; However, the de/serialized object's type gets implicitly altered to PersistentHashMap.
  ;; This type change leads to inconsistent encoding, leading to BUG #141.
  ;; TODO: Reproduce value-equality & type-mismatch bug without dependency on a 3rd party library.
  (let [;; Encoding inconsistency happens only when count of keys in FastStruct are greater than 8.
        slots (HashMap. {:a 0 :b 1 :c 2 :d 3 :e 4 :f 5 :g 6 :h 7 :i 8})
        vals [1 2 3 4 5 6 7 8 9]
        arg (FastStruct. slots vals)]
    ;; Test value-equality post de/serialization.
    (is (= arg (u/decode (u/encode arg))))
    ;; Expect encoding inconsistency post de/serialization.
    (is (false? (s/valid? ::specs/args-serializable? arg)))))

(deftest specs-test
  (specs/instrument)
  (are [sut]
    (is
      ;; When specs are instrumented, expect exceptions for incorrect parameters.
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
    #(c/perform-async tu/redis-client-opts `tu/my-fn specs-test)

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
    #(statsd/new (assoc statsd/default-opts :tags '("service:maverick")))

    ;; Console
    #(console/app-handler (assoc tu/redis-console-opts :broker {}) {})
    #(console/app-handler (dissoc tu/redis-console-opts :broker) {})
    #(console/app-handler (dissoc tu/redis-console-opts :app-name) {})
    #(console/app-handler (assoc tu/redis-console-opts :app-name :invalid-type) {})
    #(console/app-handler (dissoc tu/redis-console-opts :route-prefix) {})
    #(console/app-handler (assoc tu/redis-console-opts :route-prefix :invalid-type) {})))
