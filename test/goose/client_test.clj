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
        (sut/async 'placeholder-fn))))

  (testing "validates function is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unresolvable function"
        (sut/async `bar))))

  ; TODO: validate args are serializable, and a persistent list.

  (testing "validates retries are non-negative"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with negative retries"
        (sut/async `placeholder-fn {:retries -1}))))

  ; TODO: Uncomment test once redis URL is configurable.
  (comment
    (testing "enqueues a job to redis"
      (is (uuid?
            (java.util.UUID/fromString
              (sut/async
                `placeholder-fn
                {:args    '(1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2 true #{1 2} :a)
                 :retries 5}))))
      ; TODO: RPOP from redis to assert correct serialization.
      )))