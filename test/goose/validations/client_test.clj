(ns goose.validations.client-test
  (:require
    [goose.brokers.redis :as redis]
    [goose.client :as sut]
    [goose.defaults :as d]

    [clojure.test :refer [deftest is testing]]))

(defn placeholder-fn [])

(deftest perform-async-test
  (let [opts (assoc sut/default-opts
               :broker-opts {:redis redis/default-opts})]
    (testing "execute-fn-sym is qualified"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"execute-fn-sym should be qualified"
          (sut/perform-async opts 'placeholder-fn))))

    (testing "execute-fn-sym is resolvable"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"execute-fn-sym should be resolvable"
          (sut/perform-async opts `bar))))

    (testing "args are serializable"
      (is
        (thrown-with-msg?
          java.lang.RuntimeException
          #"args should be serializable"
          (sut/perform-async opts `placeholder-fn #(fn [])))))

    (testing "queue is unprefixed"
      (is
        (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #":queue shouldn't be prefixed"
          (sut/perform-async (assoc opts :queue (str d/queue-prefix "olttwa")) `placeholder-fn))))))

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
