(ns goose.client-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [goose.client :as sut]
    [goose.config :as cfg]
    [goose.client :as c]))

(defn placeholder-fn [])

(deftest async-test
  (testing "redis URL is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/async (assoc c/default-opts :redis-url "redis://invalid-url") 'placeholder-fn))))

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/async (assoc c/default-opts :redis-pool-opts :invalid-pool) 'placeholder-fn))))

  (testing "function symbol is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unqualified function"
        (sut/async c/default-opts 'placeholder-fn))))

  (testing "function is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with unresolvable function"
        (sut/async c/default-opts `bar))))

  (testing "queue is not prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc c/default-opts :queue (str cfg/queue-prefix "test")) `placeholder-fn))))

  (testing "queue is a string"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc c/default-opts :queue :non-string-queue) `placeholder-fn))))

  (testing "queue length is below 1000"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc c/default-opts :queue (str (range 300))) `placeholder-fn))))

  (testing "queue isn't a protected keyword"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid queue"
        (sut/async (assoc c/default-opts :queue cfg/scheduled-queue) `placeholder-fn))))

  (testing "retries are positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Called with negative retries"
        (sut/async (assoc c/default-opts :retries -1) `placeholder-fn))))

  (testing "args are serializable"
    (is
      (thrown-with-msg?
        java.lang.RuntimeException
        #"Called with unserializable args"
        (sut/async c/default-opts `placeholder-fn #(fn []))))))
