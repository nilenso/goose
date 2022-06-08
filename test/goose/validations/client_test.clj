(ns goose.validations.client-test
  (:require
    [goose.defaults :as d]
    [clojure.test :refer [deftest is testing]]
    [goose.client :as sut]))

(defn placeholder-fn [])

(deftest perform-async-test
  (testing "execute-fn-sym is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"execute-fn-sym should be qualified"
        (sut/perform-async sut/default-opts 'placeholder-fn))))

  (testing "execute-fn-sym is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"execute-fn-sym should be resolvable"
        (sut/perform-async sut/default-opts `bar))))

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"args should be serializable"
        (sut/perform-async sut/default-opts `placeholder-fn #(fn [])))))

  (testing "queue is unprefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":queue shouldn't be prefixed"
        (sut/perform-async (assoc sut/default-opts :queue (str d/queue-prefix "olttwa")) `placeholder-fn)))))

(deftest perform-at-test
  (testing "date-time is an instance of date object"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"date-time should be an instance of date object"
        (sut/perform-at {} "27-May-2022" `placeholder-fn)))))

(deftest perform-in-sec-test
  (testing "seconds are an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"seconds should be an integer"
        (sut/perform-in-sec {} 0.2 `placeholder-fn)))))
