(ns goose.rmq.integration-test
  (:require
    [goose.brokers.rmq.broker :as rmq]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    (clojure.lang ExceptionInfo)
    [java.util UUID]
    (java.util.concurrent TimeoutException)
    (java.time Instant)))


; ======= Setup & Teardown ==========
(use-fixtures :each tu/rmq-fixture)

; ======= TEST: Async execution (classic queue) ==========
(def perform-async-fn-executed (atom (promise)))
(defn perform-async-fn [arg]
  (deliver @perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "[rmq] Goose executes a function asynchronously"
    (reset! perform-async-fn-executed (promise))
    (let [arg "async-execute-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-async tu/rmq-client-opts `perform-async-fn arg)))))
          worker (w/start tu/rmq-worker-opts)]
      (is (= arg (deref @perform-async-fn-executed 100 :e2e-test-timed-out)))
      (w/stop worker))))

; ======= TEST: Async execution (quorum queue) ==========
(def quorum-fn-executed (atom (promise)))
(defn quorum-fn [arg]
  (deliver @quorum-fn-executed arg))

(deftest quorum-queue-test
  (testing "[rmq] Goose enqueues jobs to quorum queues"
    (reset! quorum-fn-executed (promise))

    (let [queue "quorum-test"
          arg "quorum-arg"
          opts (assoc tu/rmq-opts :queue-type rmq-queue/quorum)
          producer (rmq/new-producer opts)
          client-opts {:queue      queue
                       :retry-opts retry/default-opts
                       :broker     producer}

          consumer (rmq/new-consumer opts)
          worker-opts (assoc tu/worker-opts
                        :broker consumer
                        :queue queue)

          _ (is (uuid? (UUID/fromString (:id (c/perform-async client-opts `quorum-fn arg)))))
          worker (w/start worker-opts)]
      (is (= arg (deref @quorum-fn-executed 100 :quorum-test-timed-out)))
      (w/stop worker)
      (rmq/close producer)
      (rmq/close consumer))))

; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (atom (promise)))
(defn perform-in-sec-fn [arg]
  (deliver @perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "[rmq] Goose executes a function scheduled in future"
    (reset! perform-in-sec-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-in-sec tu/rmq-client-opts 1 `perform-in-sec-fn arg)))))
          worker (w/start tu/rmq-worker-opts)]
      (is (= arg (deref @perform-in-sec-fn-executed 1100 :scheduler-test-timed-out)))
      (w/stop worker)))

  (testing "[rmq] Scheduling beyond max_delay limit"
    (is
      (thrown-with-msg?
        ExceptionInfo
        #"MAX_DELAY limit breached*"
        (c/perform-in-sec tu/rmq-client-opts 4294968 `perform-in-sec-fn)))))

; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (atom (promise)))
(defn perform-at-fn [arg]
  (deliver @perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "[rmq] Goose executes a function scheduled in past"
    (reset! perform-at-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-at tu/rmq-client-opts (Instant/now) `perform-at-fn arg)))))
          scheduler (w/start tu/rmq-worker-opts)]
      (is (= arg (deref @perform-at-fn-executed 100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

; ======= TEST: Publisher Confirms =======
(def ack-handler-called (atom (promise)))
(defn test-ack-handler [tag _]
  (deliver @ack-handler-called tag))
(defn test-nack-handler [_ _])
(deftest publisher-confirm-test
  ; This test fails quite rarely.
  ; RMQ confirms in 1ms too sometimes ¯\_(ツ)_/¯
  ; Remove this test if it happens often.
  (testing "[rmq][sync-confirms] Publish timed out"
    (let [opts (assoc tu/rmq-opts
                 :publisher-confirms {:strategy d/sync-confirms :timeout-ms 1})
          producer (rmq/new-producer opts 1)
          client-opts {:queue      "sync-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     producer}]
      (is
        (thrown?
          TimeoutException
          (c/perform-async client-opts `tu/my-fn)))
      (rmq/close producer)))

  (testing "[rmq][async-confirms] Ack handler called"
    (reset! ack-handler-called (promise))
    (let [opts (assoc tu/rmq-opts
                 :publisher-confirms {:strategy     d/async-confirms
                                      :ack-handler  `test-ack-handler
                                      :nack-handler `test-nack-handler})
          producer (rmq/new-producer opts 1)
          client-opts {:queue      "async-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     producer}
          delivery-tag (:delivery-tag (c/perform-in-sec client-opts 1 `tu/my-fn))]
      (is (= delivery-tag (deref @ack-handler-called 100 :async-publisher-confirm-test-timed-out)))
      (rmq/close producer))))

; ======= TEST: Graceful shutdown ==========
(def sleepy-fn-called (atom (promise)))
(def sleepy-fn-completed (atom (promise)))
(defn sleepy-fn
  [arg]
  (deliver @sleepy-fn-called arg)
  (Thread/sleep 2000)
  (deliver @sleepy-fn-completed arg))

(deftest graceful-shutdown-test
  (testing "[rmq] Goose shuts down a worker gracefully"
    (reset! sleepy-fn-called (promise))
    (reset! sleepy-fn-completed (promise))
    (let [arg "graceful-shutdown-test"
          _ (c/perform-async tu/rmq-client-opts `sleepy-fn arg)
          worker (w/start (assoc tu/rmq-worker-opts :graceful-shutdown-sec 2))]
      (is (= arg (deref @sleepy-fn-called 100 :graceful-shutdown-test-timed-out)))
      (w/stop worker)
      (is (= arg (deref @sleepy-fn-completed 100 :non-graceful-shutdown))))))

; ======= TEST: Middleware & RMQ Metadata ==========
(def middleware-called (atom (promise)))
(defn test-middleware
  [next]
  (fn [{:keys [metadata] :as opts} job]
    (deliver @middleware-called metadata)
    (next opts job)))

(deftest middleware-test
  (testing "[rmq] Goose calls middleware & attaches RMQ metadata to opts"
    (reset! middleware-called (promise))
    (let [worker (w/start (assoc tu/rmq-worker-opts
                            :middlewares test-middleware))
          _ (c/perform-async tu/rmq-client-opts `tu/my-fn :arg1)]
      (is (= d/content-type (:content-type (deref @middleware-called 100 :middleware-test-timed-out))))
      (w/stop worker))))

; ======= TEST: Error handling transient failure job using custom retry queue ==========
(def retry-queue "test-retry")
(defn immediate-retry [_] 1)

(def failed-on-execute (atom (promise)))
(def failed-on-1st-retry (atom (promise)))
(def succeeded-on-2nd-retry (atom (promise)))

(defn retry-test-error-handler [_ _ ex]
  (if (realized? @failed-on-execute)
    (deliver @failed-on-1st-retry ex)
    (deliver @failed-on-execute ex)))

(defn erroneous-fn [arg]
  (when-not (realized? @failed-on-execute)
    (/ 1 0))
  (when-not (realized? @failed-on-1st-retry)
    (throw (ex-info "error" {})))
  (deliver @succeeded-on-2nd-retry arg))
(deftest retry-test
  (testing "[rmq] Goose retries an erroneous function"
    (reset! failed-on-execute (promise))
    (reset! failed-on-1st-retry (promise))
    (reset! succeeded-on-2nd-retry (promise))
    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue retry-queue
                       :error-handler-fn-sym `retry-test-error-handler)
          _ (c/perform-async (assoc tu/rmq-client-opts :retry-opts retry-opts) `erroneous-fn arg)
          worker (w/start tu/rmq-worker-opts)
          retry-worker (w/start (assoc tu/rmq-worker-opts :queue retry-queue))]
      (is (= ArithmeticException (type (deref @failed-on-execute 100 :retry-execute-timed-out))))
      (w/stop worker)

      (is (= ExceptionInfo (type (deref @failed-on-1st-retry 1100 :1st-retry-timed-out))))

      (is (= arg (deref @succeeded-on-2nd-retry 1100 :2nd-retry-timed-out)))
      (w/stop retry-worker))))

; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (atom (promise)))
(defn dead-test-error-handler [_ _ _])
(defn dead-test-death-handler [_ _ ex]
  (deliver @job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn []
  (swap! dead-job-run-count inc)
  (/ 1 0))

(deftest dead-test
  (testing "[rmq] Goose marks a job as dead upon reaching max retries"
    (reset! job-dead (promise))
    (reset! dead-job-run-count 0)
    (let [dead-job-opts (assoc retry/default-opts
                          :max-retries 1
                          :retry-delay-sec-fn-sym `immediate-retry
                          :error-handler-fn-sym `dead-test-error-handler
                          :death-handler-fn-sym `dead-test-death-handler)
          _ (c/perform-async (assoc tu/rmq-client-opts :retry-opts dead-job-opts) `dead-fn)
          worker (w/start tu/rmq-worker-opts)]
      (is (= ArithmeticException (type (deref @job-dead 1100 :death-handler-timed-out))))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))
