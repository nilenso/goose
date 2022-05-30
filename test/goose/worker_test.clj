(ns goose.worker-test
  (:require
    [goose.defaults :as d]
    [goose.worker :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest start-stop-test
  (testing "redis URL is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/start (assoc sut/default-opts :redis-url "redis://invalid-url")))))

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/start (assoc sut/default-opts :redis-pool-opts :invalid-pool)))))

  (testing "queues aren't prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue shouldn't be prefixed"
        (sut/start (assoc sut/default-opts :queue (str d/queue-prefix "test"))))))

  (testing "thread-count is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Thread count should be a positive integer"
        (sut/start (assoc sut/default-opts :threads 0)))))

  (testing "Graceful shutdown time is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown should be a positive integer"
        (sut/start (assoc sut/default-opts :graceful-shutdown-time-sec -1)))))

  (testing "Graceful shutdown time is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown should be a positive integer"
        (sut/start (assoc sut/default-opts :graceful-shutdown-time-sec 1.1)))))

  (testing "Scheduler polling interval is a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Scheduler polling interval should be a positive integer"
        (sut/start (assoc sut/default-opts :scheduler-polling-interval-sec -1.2))))))
