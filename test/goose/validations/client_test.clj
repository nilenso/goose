(ns goose.validations.client-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [goose.client :as sut]))

(defn placeholder-fn [])

(deftest async-test
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

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"Args should be Serializable"
        (sut/async sut/default-opts `placeholder-fn #(fn []))))))
