(ns goose.client-test
  (:require
    [clojure.test :refer :all]
    [goose.client :as sut]))

(defn placeholder-fn [])

(deftest async-test
  (testing "validates function symbol is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unqualified function"
        (sut/async nil 'placeholder-fn))))

  (testing "validates function is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unresolvable function"
        (sut/async nil `bar))))

  ; TODO: validate args are serializable, and a persistent list.

  (testing "validates retries are non-negative"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with negative retries"
        (sut/async nil `placeholder-fn {:retries -1})))))
; NOTE: Full-cycle happy-path test is written in worker_test.clj.
