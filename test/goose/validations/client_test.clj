(ns goose.validations.client-test
  (:require
    [goose.defaults :as d]
    [clojure.test :refer [deftest is testing]]
    [goose.client :as sut]))

(defn placeholder-fn [])

(deftest async-test
  (testing "execute-fn-sym is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"execute-fn-sym should be qualified"
        (sut/async sut/default-opts 'placeholder-fn))))

  (testing "execute-fn-sym is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"execute-fn-sym should be resolvable"
        (sut/async sut/default-opts `bar))))

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"args should be serializable"
        (sut/async sut/default-opts `placeholder-fn #(fn [])))))

  (testing "queue is unprefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":queue shouldn't be prefixed"
        (sut/async (assoc sut/default-opts :queue (str d/queue-prefix "olttwa")) `placeholder-fn))))

  (testing "schedule is an epoch"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":schedule should be an integer denoting epoch in milliseconds"
        (sut/async (assoc sut/default-opts :schedule "non-int") `placeholder-fn)))))
