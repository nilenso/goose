(ns goose.validations.scheduler-test
  (:require
    [goose.validations.scheduler :as sut]

    [clojure.test :refer [deftest is testing]])
  (:import
    [java.util Date]))

(deftest validate-scheduler-test
  (testing "perform-at and perform-in-sec are mutually exclusive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at & :perform-in-sec are mutually exclusive options"
        (sut/validate-schedule {:perform-at (Date.) :perform-in-sec 1})))

    (testing ":perform-in-sec is a positive integer"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #":perform-in-sec isn't a positive integer"
          (sut/validate-schedule {:perform-in-sec 0.2})))))

  (testing ":perform-at is an instance of date object"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at isn't an instance of date object"
        (sut/validate-schedule {:perform-at "27-May-2022"})))))
