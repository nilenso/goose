(ns goose.validations.redis-test
  (:require
    [goose.validations.redis :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest validate-redis-test
  (testing "redis URL is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis URL"
        (sut/validate-redis {:redis-url "redis://invalid-url"}))))

  (testing "redis conn pool opt is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid redis pool opts"
        (sut/validate-redis {:redis-url "redis://username:password@my-redis-instance:123" :redis-pool-opts :invalid-pool})))))
