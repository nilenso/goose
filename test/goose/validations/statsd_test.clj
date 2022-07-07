(ns goose.validations.statsd-test
  (:require
    [goose.validations.statsd :as sut]

    [clojure.test :refer [deftest is testing]]))

(deftest validate-statsd-test
  (testing "sample-rate is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"sample-rate should be a double"
        (sut/validate-statsd {:sample-rate 1}))))

  (testing "tags should be a set"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"tags should be a set"
        (sut/validate-statsd {:sample-rate 1.0 :tags '("service:maverick")})))))
