(ns goose.validations.retry-test
  (:require
    [goose.validations.retry :as sut]
    [goose.retry :as retry]

    [clojure.test :refer [deftest is testing]]))

(defn dummy-fn [_] "dummy")

(deftest validate-retry-test
  (testing "max retry count is positive"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Max retry count shouldn't be negative"
        (sut/validate-retry (assoc retry/default-opts :max-retries -1)))))

  (testing "max retry count is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Max retry count should be an integer"
        (sut/validate-retry (assoc retry/default-opts :max-retries 1.1)))))

  (testing "Error handler is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Error handler should be qualified"
        (sut/validate-retry (assoc retry/default-opts :error-handler-fn-sym 'dummy-fn)))))

  (testing "Error handler is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Error handler should be resolvable"
        (sut/validate-retry (assoc retry/default-opts :error-handler-fn-sym `unresolvable)))))


  (testing "Error handler has arity of 2 args"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Error handler should have arity of 2 args"
        (sut/validate-retry (assoc retry/default-opts :error-handler-fn-sym `dummy-fn)))))

  (testing "Death handler is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Death handler should be qualified"
        (sut/validate-retry (assoc retry/default-opts :death-handler-fn-sym 'dummy-fn)))))

  (testing "Death handler is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Death handler should be resolvable"
        (sut/validate-retry (assoc retry/default-opts :death-handler-fn-sym `unresolvable)))))


  (testing "Death handler has arity of 2 args"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Death handler should have arity of 2 args"
        (sut/validate-retry (assoc retry/default-opts :death-handler-fn-sym `dummy-fn)))))


  (testing "Retry delay sec is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Retry delay sec should be qualified"
        (sut/validate-retry (assoc retry/default-opts :retry-delay-sec-fn-sym 'dummy-fn)))))

  (testing "Retry delay sec is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Retry delay sec should be resolvable"
        (sut/validate-retry (assoc retry/default-opts :retry-delay-sec-fn-sym `unresolvable)))))

  (testing "Retry delay sec returns a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Retry delay sec should return a positive integer"
        (sut/validate-retry (assoc retry/default-opts :retry-delay-sec-fn-sym `dummy-fn)))))

  (testing "retry-queue is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue should be a string"
        (sut/validate-retry (assoc retry/default-opts :retry-queue :invalid)))))

  (testing "skip-dead-queue is a boolean"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"skip-dead-queue should be a boolean"
        (sut/validate-retry (assoc retry/default-opts :skip-dead-queue 1))))))
