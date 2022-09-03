(ns goose.redis.integration-test
  (:require
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    [java.util UUID]))

; ======= Setup & Teardown ==========
(use-fixtures :once tu/redis-fixture)

; ======= TEST: Async execution ==========
(def perform-async-fn-executed (promise))
(defn perform-async-fn [arg]
  (deliver perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "Goose executes a function asynchronously"
    (let [arg "async-execute-test"
          worker (w/start tu/redis-worker-opts)]
      (is (uuid? (UUID/fromString (:id (c/perform-async tu/redis-client-opts `perform-async-fn arg)))))
      (is (= arg (deref perform-async-fn-executed 200 :e2e-test-timed-out)))
      (w/stop worker))))

; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (promise))
(defn perform-in-sec-fn [arg]
  (deliver perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "Goose executes a function scheduled in future"
    (let [arg "scheduling-test"
          scheduler (w/start tu/redis-worker-opts)]
      (is (uuid? (UUID/fromString (:id (c/perform-in-sec tu/redis-client-opts 1 `perform-in-sec-fn arg)))))
      (is (= arg (deref perform-in-sec-fn-executed 4200 :scheduler-test-timed-out)))
      (w/stop scheduler))))

; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (promise))
(defn perform-at-fn [arg]
  (deliver perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "Goose executes a function scheduled in past"
    (let [arg "scheduling-test"
          scheduler (w/start tu/redis-worker-opts)]
      (is (uuid? (UUID/fromString (:id (c/perform-at tu/redis-client-opts (java.time.Instant/now) `perform-at-fn arg)))))
      (is (= arg (deref perform-at-fn-executed 200 :scheduler-test-timed-out)))
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
  (testing "Goose retries an erroneous function"
    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue retry-queue
                       :error-handler-fn-sym `retry-test-error-handler)
          worker (w/start tu/redis-worker-opts)
          retry-worker (w/start (assoc tu/redis-worker-opts :queue retry-queue))]
      (c/perform-async (assoc tu/redis-client-opts :retry-opts retry-opts) `erroneous-fn arg)

      (is (= java.lang.ArithmeticException (type (deref failed-on-execute 200 :retry-execute-timed-out))))
      (w/stop worker)

      (is (= clojure.lang.ExceptionInfo (type (deref failed-on-1st-retry 4200 :1st-retry-timed-out))))

      (is (= arg (deref succeeded-on-2nd-retry 4200 :2nd-retry-timed-out)))
      (w/stop retry-worker))))

; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (promise))
(defn dead-test-error-handler [_ _ _])
(defn dead-test-death-handler [_ _ ex]
  (deliver job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn []
  (swap! dead-job-run-count inc)
  (/ 1 0))

(deftest dead-test
  (testing "Goose marks a job as dead upon reaching max retries"
    (let [dead-job-opts (assoc retry/default-opts
                          :max-retries 1
                          :retry-delay-sec-fn-sym `immediate-retry
                          :error-handler-fn-sym `dead-test-error-handler
                          :death-handler-fn-sym `dead-test-death-handler)
          worker (w/start tu/redis-worker-opts)]
      (c/perform-async (assoc tu/redis-client-opts :retry-opts dead-job-opts) `dead-fn)

      (is (= java.lang.ArithmeticException (type (deref job-dead 4200 :death-handler-timed-out))))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))
