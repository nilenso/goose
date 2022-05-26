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

(def scheduled-fn-called (promise))
(defn scheduled-fn [arg]
  (deliver scheduled-fn-called arg))

(def client-opts
  (assoc c/default-opts
    :queue "test"
    :redis-url redis-url))

(def worker-opts
  (assoc w/default-opts
    :redis-url redis-url
    :queue "test"
    :graceful-shutdown-time-sec 1
    :scheduled-jobs-polling-interval-sec 1))

(deftest enqueue-dequeue-execute-test
  (testing "Goose executes a function asynchronously"
    (let [worker (w/start worker-opts)]
      (is (uuid? (UUID/fromString (c/async client-opts `placeholder-fn "e2e-test"))))
      (is (= "e2e-test" (deref fn-called 100 :e2e-test-timed-out)))
      (w/stop worker)))

  (testing "Goose executes a scheduled function asynchronously"
    (c/async
      (assoc client-opts
        :schedule {:perform-in-sec 1})
      `scheduled-fn "scheduling-test")
    (let [scheduler (w/start worker-opts)]
      (is (= "scheduling-test" (deref scheduled-fn-called 1100 :scheduler-test-timed-out)))
      (w/stop scheduler))))
