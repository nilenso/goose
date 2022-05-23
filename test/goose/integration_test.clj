(ns goose.integration-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [goose.worker :as w]
    [goose.client :as c])
  (:import
    [java.util UUID]))

(def redis-url
  (let [host (or (System/getenv "GOOSE_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(def fn-called (promise))

(defn placeholder-fn [arg]
  (deliver fn-called arg))

(deftest enqueue-dequeue-execute-test
  (testing "Goose executes a function asynchronously"
    (let [opts {:redis-url redis-url}
          worker (w/start opts)]
      (is (uuid? (UUID/fromString (c/async opts `placeholder-fn "e2e-test"))))
      (is (= "e2e-test" (deref fn-called 100 :e2e-test-timed-out)))
      (w/stop worker))))
