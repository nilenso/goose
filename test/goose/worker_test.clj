(ns goose.worker-test
  (:require
    [clojure.test :refer :all]
    [goose.worker :as sut]
    [goose.config :as cfg]))

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
        (sut/start {:queues '((str (cfg/queue-prefix "test")))}))))

  (testing "parallelism is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Parallelism isn't a positive integer"
        (sut/start {:parallelism 0}))))

  (testing "Graceful shutdown time is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid graceful shutdown time"
        (sut/start {:graceful-shutdown-time-sec -1}))))

  (testing "Graceful shutdown time is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid graceful shutdown time"
        (sut/start {:graceful-shutdown-time-sec 1.1})))))
