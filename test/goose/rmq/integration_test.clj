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
