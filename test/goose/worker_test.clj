(ns goose.worker-test
  (:require
    [goose.config :as cfg]
    [goose.worker :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest start-stop-test
  (testing "redis URL is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/start {:redis-url "redis://invalid-url"}))))

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/start {:redis-pool-opts :invalid-pool}))))

  (testing "queues are valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queues"
        (sut/start {:queues "invalid queue"}))))

  (testing "queues aren't prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queues"
        (sut/start {:queues [(str cfg/queue-prefix "test")]}))))

  (testing "thread-count is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Thread count isn't a positive integer"
        (sut/start {:threads 0}))))

  (testing "Graceful shutdown time is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown time isn't a positive integer"
        (sut/start {:graceful-shutdown-time-sec -1}))))

  (testing "Graceful shutdown time is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown time isn't a positive integer"
        (sut/start {:graceful-shutdown-time-sec 1.1})))))
