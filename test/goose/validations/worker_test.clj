(ns goose.validations.worker-test
  (:require
    [goose.worker :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest start-test
  (testing "thread-count is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Thread count should be a positive integer"
        (sut/start (assoc sut/default-opts :threads 0)))))

  (testing "thread-count is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Thread count should be a positive integer"
        (sut/start (assoc sut/default-opts :threads 1.1)))))

  (testing "graceful shutdown time is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown should be a positive integer"
        (sut/start (assoc sut/default-opts :graceful-shutdown-time-sec -1)))))

  (testing "graceful shutdown time is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Graceful shutdown should be a positive integer"
        (sut/start (assoc sut/default-opts :graceful-shutdown-time-sec 1.1)))))

  (testing "scheduler polling interval is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Scheduler polling interval should be a positive integer"
        (sut/start (assoc sut/default-opts :scheduler-polling-interval-sec -1.2)))))

  (testing "scheduler polling interval is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Scheduler polling interval should be a positive integer"
        (sut/start (assoc sut/default-opts :scheduler-polling-interval-sec 1.1))))))
