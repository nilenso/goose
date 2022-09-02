(ns goose.rmq.integration-test
  (:require
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    [java.util UUID]))


; ======= Setup & Teardown ==========
(use-fixtures :once tu/rmq-fixture)

; ======= TEST: Async execution ==========
(def perform-async-fn-executed (promise))
(defn perform-async-fn [arg]
  (deliver perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "[rmq] Goose executes a function asynchronously"
    (let [arg "async-execute-test"
          worker (w/start tu/rmq-worker-opts)]
      (is (uuid? (UUID/fromString (c/perform-async tu/rmq-client-opts `perform-async-fn arg))))
      (is (= arg (deref perform-async-fn-executed 100 :e2e-test-timed-out)))
      (w/stop worker))))

; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (promise))
(defn perform-in-sec-fn [arg]
  (deliver perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "[rmq] Goose executes a function scheduled in future"
    (let [arg "scheduling-test"
          _ (c/perform-in-sec tu/rmq-client-opts 1 `perform-in-sec-fn arg)
          worker (w/start tu/rmq-worker-opts)]
      (is (= arg (deref perform-in-sec-fn-executed 1100 :scheduler-test-timed-out)))
      (w/stop worker)))

  (testing "[rmq] Scheduling beyond max_delay limit"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"MAX_DELAY limit breached*"
        (c/perform-in-sec tu/rmq-client-opts 4294968 `perform-in-sec-fn)))))

; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (promise))
(defn perform-at-fn [arg]
  (deliver perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "[rmq] Goose executes a function scheduled in past"
    (let [arg "scheduling-test"
          _ (c/perform-at tu/rmq-client-opts (java.time.Instant/now) `perform-at-fn arg)
          scheduler (w/start tu/rmq-worker-opts)]
      (is (= arg (deref perform-at-fn-executed 100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

; ======= TEST: Error handling transient failure job using custom retry queue ==========
(def retry-queue "test-retry")
(defn immediate-retry [_] 1)

(def failed-on-execute (promise))
(def failed-on-1st-retry (promise))
(def succeeded-on-2nd-retry (promise))

(defn retry-test-error-handler [_ _ ex]
  (if (realized? failed-on-execute)
    (deliver failed-on-1st-retry ex)
    (deliver failed-on-execute ex)))

(defn erroneous-fn [arg]
  (when-not (realized? failed-on-execute)
    (/ 1 0))
  (when-not (realized? failed-on-1st-retry)
    (throw (ex-info "error" {})))
  (deliver succeeded-on-2nd-retry arg))
(deftest retry-test
  (testing "[rmq] Goose retries an erroneous function"
    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue retry-queue
                       :error-handler-fn-sym `retry-test-error-handler)
          worker (w/start tu/rmq-worker-opts)
          retry-worker (w/start (assoc tu/rmq-worker-opts :queue retry-queue))]
      (c/perform-async (assoc tu/rmq-client-opts :retry-opts retry-opts) `erroneous-fn arg)

      (is (= java.lang.ArithmeticException (type (deref failed-on-execute 100 :retry-execute-timed-out))))
      (w/stop worker)

      (is (= clojure.lang.ExceptionInfo (type (deref failed-on-1st-retry 1100 :1st-retry-timed-out))))

      (is (= arg (deref succeeded-on-2nd-retry 1100 :2nd-retry-timed-out)))
      (w/stop retry-worker))))
