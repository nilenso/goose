(ns goose.brokers.redis.integration-test
  (:require
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    [clojure.lang ExceptionInfo]
    [java.time Instant]
    [java.util UUID]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ======= TEST: Async execution ==========
(def perform-async-fn-executed (atom (promise)))
(defn perform-async-fn [arg]
  (deliver @perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "Goose executes a function asynchronously"
    (reset! perform-async-fn-executed (promise))
    (let [arg "async-execute-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-async tu/redis-client-opts `perform-async-fn arg)))))
          worker (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-async-fn-executed 100 :e2e-test-timed-out)))
      (w/stop worker))))

;;; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (atom (promise)))
(defn perform-in-sec-fn [arg]
  (deliver @perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "Goose executes a function scheduled in future"
    (reset! perform-in-sec-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-in-sec tu/redis-client-opts 1 `perform-in-sec-fn arg)))))
          scheduler (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-in-sec-fn-executed 2100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

;;; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (atom (promise)))
(defn perform-at-fn [arg]
  (deliver @perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "Goose executes a function scheduled in past"
    (reset! perform-at-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-at tu/redis-client-opts (Instant/now) `perform-at-fn arg)))))
          scheduler (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-at-fn-executed 100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

;;; ======= TEST: Middleware ==========
(def middleware-called (atom (promise)))
(defn add-five [arg] (+ 5 arg))
(defn test-middleware
  [next]
  (fn [opts job]
    (let [result (next opts job)]
      (deliver @middleware-called result))))

(deftest middleware-test
  (testing "[rmq] Goose calls middleware & attaches RMQ metadata to opts"
    (reset! middleware-called (promise))
    (let [worker (w/start (assoc tu/redis-worker-opts
                            :middlewares test-middleware))
          _ (c/perform-async tu/redis-client-opts `add-five 5)]
      (is (= 10 (deref @middleware-called 100 :middleware-test-timed-out)))
      (w/stop worker))))

;;; ======= TEST: Error handling transient failure job using custom retry queue ==========
(def retry-queue "test-retry")
(defn immediate-retry [_] 1)

(def failed-on-execute (atom (promise)))
(def failed-on-1st-retry (atom (promise)))
(def succeeded-on-2nd-retry (atom (promise)))
(def retry-error-service (atom (promise)))

(defn retry-test-error-handler
  [config _ ex]
  (deliver @retry-error-service config)
  (if (realized? @failed-on-execute)
    (deliver @failed-on-1st-retry ex)
    (deliver @failed-on-execute ex)))

(defn erroneous-fn
  [arg]
  (when-not (realized? @failed-on-execute)
    (/ 1 0))
  (when-not (realized? @failed-on-1st-retry)
    (throw (ex-info "error" {})))
  (deliver @succeeded-on-2nd-retry arg))
(deftest retry-test
  (testing "Goose retries an erroneous function"
    (reset! failed-on-execute (promise))
    (reset! failed-on-1st-retry (promise))
    (reset! succeeded-on-2nd-retry (promise))
    (reset! retry-error-service (promise))

    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue retry-queue
                       :error-handler-fn-sym `retry-test-error-handler)
          _ (c/perform-async (assoc tu/redis-client-opts :retry-opts retry-opts) `erroneous-fn arg)
          error-svc-cfg :my-retry-test-config
          worker-opts (assoc tu/redis-worker-opts :error-service-config error-svc-cfg)
          worker (w/start worker-opts)
          retry-worker (w/start (assoc worker-opts :queue retry-queue))]
      (is (= ArithmeticException (type (deref @failed-on-execute 100 :retry-execute-timed-out))))
      (is (= error-svc-cfg (deref @retry-error-service 1 :retry-error-svc-cfg-timed-out)))
      (w/stop worker)

      (is (= ExceptionInfo (type (deref @failed-on-1st-retry 3100 :1st-retry-timed-out))))

      (is (= arg (deref @succeeded-on-2nd-retry 3100 :2nd-retry-timed-out)))
      (w/stop retry-worker))))

;;; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (atom (promise)))
(def death-error-service (atom (promise)))

(defn dead-test-error-handler [_ _ _])
(defn dead-test-death-handler
  [config _ ex]
  (deliver @death-error-service config)
  (deliver @job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn
  []
  (swap! dead-job-run-count inc)
  (/ 1 0))

(deftest death-test
  (testing "Goose marks a job as dead upon reaching max retries"
    (reset! job-dead (promise))
    (reset! death-error-service (promise))
    (reset! dead-job-run-count 0)
    (let [dead-job-opts (assoc retry/default-opts
                          :max-retries 1
                          :retry-delay-sec-fn-sym `immediate-retry
                          :error-handler-fn-sym `dead-test-error-handler
                          :death-handler-fn-sym `dead-test-death-handler)
          _ (c/perform-async (assoc tu/redis-client-opts :retry-opts dead-job-opts) `dead-fn)
          error-svc-cfg :my-death-test-config
          worker-opts (assoc tu/redis-worker-opts :error-service-config error-svc-cfg)
          worker (w/start worker-opts)]
      (is (= ArithmeticException (type (deref @job-dead 2100 :death-handler-timed-out))))
      (is (= error-svc-cfg (deref @death-error-service 1 :death-error-svc-cfg-timed-out)))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))
