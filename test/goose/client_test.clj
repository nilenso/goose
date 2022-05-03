(ns goose.client-test
  (:require
    [clojure.test :refer :all]
    [goose.client :as client]))

(def placeholder-fn (fn []))

(deftest async-test
  (testing "validates retries are non-negative"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with negative retries"
        (client/async `placeholder-fn {:retries -1}))))

  (testing "validates function is a qualified symbol"
    (let [unqualified-fn-sym 'placeholder-fn]
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Called with unresolvable function"
          (client/async unqualified-fn-sym)))))

  (testing "validates function is resolvable"
    (let [unresolvable-sym `bar]
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Called with unresolvable function"
          (client/async unresolvable-sym))))))
