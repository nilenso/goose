(ns goose.rmq.integration-test
  (:require
    [goose.brokers.rmq.broker :as rmq]
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    [java.util UUID]))


; ======= Setup & Teardown ==========
(use-fixtures :each tu/rmq-fixture)

; ======= TEST: Async execution ==========
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
        clojure.lang.ExceptionInfo
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
          _ (is (uuid? (UUID/fromString (:id (c/perform-at tu/rmq-client-opts (java.time.Instant/now) `perform-at-fn arg)))))
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
  (testing "[rmq] Publish timed out"
    (let [opts {:settings           {:uri tu/rmq-url}
                :publisher-confirms {:strategy rmq-publisher-confirms/sync
                                     :timeout  1}}
          broker (rmq/new opts 1)
          client-opts {:queue      "sync-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     broker}]
      (is
        (thrown?
          java.util.concurrent.TimeoutException
          (c/perform-async client-opts `tu/my-fn)))
      (rmq/close broker)))

  (testing "[rmq] Ack handler called"
    (reset! ack-handler-called (promise))
    (let [opts {:settings           {:uri tu/rmq-url}
                :publisher-confirms {:strategy     rmq-publisher-confirms/async
                                     :ack-handler  `test-ack-handler
                                     :nack-handler `test-nack-handler}}
          broker (rmq/new opts 1)
          client-opts {:queue      "async-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     broker}
          delivery-tag (:delivery-tag (c/perform-in-sec client-opts 1 `tu/my-fn))]
      (is (= delivery-tag (deref @ack-handler-called 100 :async-publisher-confirm-test-timed-out)))
      (rmq/close broker))))

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
      (is (= java.lang.ArithmeticException (type (deref @failed-on-execute 100 :retry-execute-timed-out))))
      (w/stop worker)

      (is (= clojure.lang.ExceptionInfo (type (deref @failed-on-1st-retry 1100 :1st-retry-timed-out))))

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
      (is (= java.lang.ArithmeticException (type (deref @job-dead 1100 :death-handler-timed-out))))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))
