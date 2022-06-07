(ns goose.validations.scheduler-test
  (:require
    [goose.scheduler :as sut]

    [clojure.test :refer [deftest is testing]])
  (:import
    [java.util Date]))

(deftest validate-scheduler-test
  (testing ":perform-at and :perform-in-sec are mutually exclusive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at & :perform-in-sec should be mutually exclusive"
        (sut/set-schedule {} {:perform-at (Date.) :perform-in-sec 1}))))

  (testing "either :perform-at or :perform-in-sec is present"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"either :perform-at or :perform-in-sec should be present"
        (sut/set-schedule {} {}))))

  (testing ":perform-in-sec is a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-in-sec should be positive integer"
        (sut/set-schedule {} {:perform-in-sec 0.2}))))

  (testing ":perform-at is an instance of date object"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at should be an instance of date object"
        (sut/set-schedule {} {:perform-at "27-May-2022"})))))
