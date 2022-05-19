(ns goose.integration-test
  (:require
    [clojure.test :refer :all]
    [goose.worker :as w]
    [goose.client :as c])
  (:import
    [java.util UUID]))

(deftest enqueue-dequeue-execute-test
  (testing "Goose executes a function asynchronously"
    (let [fn-called (promise)
          worker (w/start {})]
      (defn placeholder-fn [arg]
        (deliver fn-called arg))
      (is (uuid? (UUID/fromString (c/async {} `placeholder-fn "e2e-test"))))
      (is (= "e2e-test" (deref fn-called 100 :e2e-test-timed-out)))
      (w/stop worker))))
