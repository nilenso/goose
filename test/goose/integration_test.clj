(ns goose.integration-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [goose.worker :as w]
    [goose.client :as c])
  (:import
    [java.util UUID]))

(def fn-called (promise))

(defn placeholder-fn [arg]
  (deliver fn-called arg))

(deftest enqueue-dequeue-execute-test
  (testing "Goose executes a function asynchronously"
    (let [worker (w/start {})]
      (is (uuid? (UUID/fromString (c/async {} `placeholder-fn "e2e-test"))))
      (is (= "e2e-test" (deref fn-called 100 :e2e-test-timed-out)))
      (w/stop worker))))
