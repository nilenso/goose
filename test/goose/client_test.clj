(ns goose.client-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [goose.client :as sut]
    [goose.defaults :as d])
  (:import
    [java.util Date]))

(defn placeholder-fn [])

(deftest async-test
  (testing "redis URL is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/async (assoc sut/default-opts :redis-url "redis://invalid-url") 'placeholder-fn))))

  (testing "function symbol is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Function symbol should be Qualified"
        (sut/async sut/default-opts 'placeholder-fn))))

  (testing "function is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Function symbol should be Resolvable"
        (sut/async sut/default-opts `bar))))


  (testing "queue is a string"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue should be a string"
        (sut/async (assoc sut/default-opts :queue :non-string-queue) `placeholder-fn))))

  (testing "queue length is below 1000"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue length should be less than 1000"
        (sut/async (assoc sut/default-opts :queue (str (range 300))) `placeholder-fn))))

  (testing "queue is not prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue shouldn't be prefixed"
        (sut/async (assoc sut/default-opts :queue (str d/queue-prefix "olttwa")) `placeholder-fn))))

  (testing "queue isn't a protected keyword"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Protected queue names shouldn't be used"
        (sut/async (assoc sut/default-opts :queue d/schedule-queue) `placeholder-fn))))

  (testing "perform-at and perform-in-sec are mutually exclusive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at & :perform-in-sec are mutually exclusive options"
        (sut/async (assoc sut/default-opts :schedule-opts {:perform-at (Date.) :perform-in-sec 1}) `placeholder-fn))))

  (testing ":perform-in-sec is a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-in-sec isn't a positive integer"
        (sut/async (assoc sut/default-opts :schedule-opts {:perform-in-sec 0.2}) `placeholder-fn))))

  (testing ":perform-at is an instance of date object"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at isn't an instance of date object"
        (sut/async (assoc sut/default-opts :schedule-opts {:perform-at "27-May-2022"}) `placeholder-fn))))

  (testing "retry count is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Retry count shouldn't be negative"
        (sut/async (assoc sut/default-opts :retry -1) `placeholder-fn))))

  (testing "retry count is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Retry count should be an integer"
        (sut/async (assoc sut/default-opts :retry 1.1) `placeholder-fn))))

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"Args should be Serializable"
        (sut/async sut/default-opts `placeholder-fn #(fn []))))))
