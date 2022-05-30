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

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/async (assoc sut/default-opts :redis-pool-opts :invalid-pool) 'placeholder-fn))))

  (testing "function symbol is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unqualified function"
        (sut/async sut/default-opts 'placeholder-fn))))

  (testing "function is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unresolvable function"
        (sut/async sut/default-opts `bar))))

  (testing "queue is not prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc sut/default-opts :queue (str d/queue-prefix "olttwa")) `placeholder-fn))))

  (testing "queue is a string"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc sut/default-opts :queue :non-string-queue) `placeholder-fn))))

  (testing "queue length is below 1000"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc sut/default-opts :queue (str (range 300))) `placeholder-fn))))

  (testing "queue isn't a protected keyword"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc sut/default-opts :queue d/schedule-queue) `placeholder-fn))))

  (testing "perform-at and perform-in-sec are mutually exclusive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at & :perform-in-sec are mutually exclusive options"
        (sut/async (assoc sut/default-opts :schedule {:perform-at (Date.) :perform-in-sec 1}) `placeholder-fn))))

  (testing ":perform-in-sec is a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-in-sec isn't a positive integer"
        (sut/async (assoc sut/default-opts :schedule {:perform-in-sec 0.2}) `placeholder-fn))))

  (testing ":perform-at is an instance of date object"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":perform-at isn't an instance of date object"
        (sut/async (assoc sut/default-opts :schedule {:perform-at "27-May-2022"}) `placeholder-fn))))

  (testing "retries are positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with negative retries"
        (sut/async (assoc sut/default-opts :retries -1) `placeholder-fn))))

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"Called with unserializable args"
        (sut/async sut/default-opts `placeholder-fn #(fn []))))))
