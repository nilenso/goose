(ns goose.brokers.rmq.console-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [goose.brokers.rmq.console :as console]
    [goose.test-utils :as tu]
    [ring.mock.request :as mock]
    [spy.core :as spy]))

(deftest get-homepage-test
  (testing "Should show rmq's home page even if no jobs in dead queue"
    (let [response (console/homepage {:console-opts tu/rmq-console-opts
                                      :prefix-route str})]
      (is (= 200 (:status response)))
      (is (str/starts-with? (:body response) "<!DOCTYPE html>")))))

(deftest page-handler-test
  (testing "Handler should invoke homepage handler"
    (with-redefs [console/homepage (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/rmq-producer (mock/request :get
                                                     "/"))
      (true? (spy/called-once? console/homepage))
      (is (= [{:status 200 :body "Mocked resp"}] (spy/responses console/homepage))))))
