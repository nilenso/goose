(ns goose.validations.worker-test
  (:require
    [goose.worker :as sut]
    [goose.brokers.redis :as redis]

    [clojure.test :refer [deftest is testing]]))

(deftest start-test
  (let [opts (assoc sut/default-opts
               :broker-opts {:redis redis/default-opts})]
    (testing "thread-count is positive"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Thread count should be a positive integer"
          (sut/start (assoc opts :threads 0)))))

    (testing "thread-count is an integer"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Thread count should be a positive integer"
          (sut/start (assoc opts :threads 1.1)))))

    (testing "graceful shutdown time is positive"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Graceful shutdown should be a positive integer"
          (sut/start (assoc opts :graceful-shutdown-sec -1)))))

    (testing "graceful shutdown time is an integer"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Graceful shutdown should be a positive integer"
          (sut/start (assoc opts :graceful-shutdown-sec 1.1)))))

    (testing "scheduler polling interval is positive"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Scheduler polling interval should be a positive integer"
          (sut/start (assoc opts :scheduler-polling-interval-sec -1.2)))))

    (testing "scheduler polling interval is an integer"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Scheduler polling interval should be a positive integer"
          (sut/start (assoc opts :scheduler-polling-interval-sec 1.1)))))))
