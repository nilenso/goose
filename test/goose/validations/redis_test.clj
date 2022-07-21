(ns goose.validations.redis-test
  (:require
    [goose.validations.redis :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest validate-redis-test
  (testing "redis-url is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/validate-redis {:url "redis://invalid-url"}))))

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/validate-redis {:url "redis://username:password@my-redis-instance:123" :pool-opts :invalid-pool})))))
