(ns goose.validations.statsd-test
  (:require
    [goose.validations.statsd :as sut]
    [goose.statsd.statsd :as statsd]

    [clojure.test :refer [deftest is testing]]))

(deftest validate-statsd-test
  (testing "host is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"host should be a string"
        (sut/validate-statsd {:host 127001}))))

  (testing "port is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"port should be a positive integer"
        (sut/validate-statsd (assoc statsd/default-opts :port "8125")))))

  (testing "sample-rate is valid"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"sample-rate should be a double"
        (sut/validate-statsd (assoc statsd/default-opts :sample-rate 1)))))

  (testing "tags should be a set"
    (is
      (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"tags should be a set"
        (sut/validate-statsd (assoc statsd/default-opts :tags '("service:maverick")))))))
