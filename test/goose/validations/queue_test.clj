(ns goose.validations.queue-test
  (:require
    [goose.validations.queue :as sut]
    [goose.defaults :as d]

    [clojure.test :refer [deftest is testing]]))

(deftest validate-queue-test
  (testing "queue is a string"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue should be a string"
        (sut/validate-queue :non-string-queue))))

  (testing "queue length is below 1000"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue length should be less than 1000"
        (sut/validate-queue (str (range 300))))))

  (testing "queue isn't a protected keyword"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Protected queue names shouldn't be used"
        (sut/validate-queue d/schedule-queue))))

  (testing "queue isn't a protected keyword"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Protected queue names shouldn't be used"
        (sut/validate-queue d/dead-queue)))))

