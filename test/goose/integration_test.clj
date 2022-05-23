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
    (is (uuid? (UUID/fromString (c/async {:redis-url "redis://redis:6379"} `placeholder-fn "e2e-test"))))))
