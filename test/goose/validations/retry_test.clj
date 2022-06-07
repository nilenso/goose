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
        #":max-retries count shouldn't be negative"
        (sut/validate-retry-opts (assoc retry/default-opts :max-retries -1)))))

  (testing "max retry count is an integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":max-retries count should be an integer"
        (sut/validate-retry-opts (assoc retry/default-opts :max-retries 1.1)))))

  (testing "retry-queue is prefixed"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":retry-queue should be prefixed"
        (sut/validate-retry-opts (assoc retry/default-opts :retry-queue "unprefixed-queue")))))

  (testing "retry-queue is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Queue should be a string"
        (sut/validate-retry-opts (assoc retry/default-opts :retry-queue :invalid)))))

  (testing ":error-handler-fn-sym is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":error-handler-fn-sym should be qualified"
        (sut/validate-retry-opts (assoc retry/default-opts :error-handler-fn-sym 'dummy-fn)))))

  (testing ":error-handler-fn-sym is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":error-handler-fn-sym should be resolvable"
        (sut/validate-retry-opts (assoc retry/default-opts :error-handler-fn-sym `unresolvable)))))


  (testing ":error-handler-fn-sym has arity of 2 args"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":error-handler-fn-sym should have arity of 2 args"
        (sut/validate-retry-opts (assoc retry/default-opts :error-handler-fn-sym `dummy-fn)))))

  (testing ":death-handler-fn-sym is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":death-handler-fn-sym should be qualified"
        (sut/validate-retry-opts (assoc retry/default-opts :death-handler-fn-sym 'dummy-fn)))))

  (testing ":death-handler-fn-sym is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":death-handler-fn-sym should be resolvable"
        (sut/validate-retry-opts (assoc retry/default-opts :death-handler-fn-sym `unresolvable)))))

  (testing ":death-handler-fn-sym has arity of 2 args"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":death-handler-fn-sym should have arity of 2 args"
        (sut/validate-retry-opts (assoc retry/default-opts :death-handler-fn-sym `dummy-fn)))))

  (testing ":retry-delay-sec-fn-sym is qualified"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":retry-delay-sec-fn-sym should be qualified"
        (sut/validate-retry-opts (assoc retry/default-opts :retry-delay-sec-fn-sym 'dummy-fn)))))

  (testing ":retry-delay-sec-fn-sym is resolvable"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":retry-delay-sec-fn-sym should be resolvable"
        (sut/validate-retry-opts (assoc retry/default-opts :retry-delay-sec-fn-sym `unresolvable)))))

  (testing ":retry-delay-sec-fn-sym returns a positive integer"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":retry-delay-sec-fn-sym should return a positive integer"
        (sut/validate-retry-opts (assoc retry/default-opts :retry-delay-sec-fn-sym `dummy-fn)))))

  (testing "skip-dead-queue is a boolean"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"skip-dead-queue should be a boolean"
        (sut/validate-retry-opts (assoc retry/default-opts :skip-dead-queue 1))))))
