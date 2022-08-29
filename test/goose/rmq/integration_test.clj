(ns goose.rmq.integration-test
  (:require
    [goose.client :as c]
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
      (w/stop worker))))

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
