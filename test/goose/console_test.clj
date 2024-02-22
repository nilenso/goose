(ns goose.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.broker :as broker]
            [goose.brokers.redis.console :as redis-console]
            [goose.console :as console]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock])
  (:import (java.lang AbstractMethodError IllegalArgumentException)))

(use-fixtures :each tu/redis-fixture tu/reset-stubs)

(deftest dispatch-handler-test
  (testing "Should dispatch to redis handler when app-handler is given redis broker details"
    (with-redefs [redis-console/handler (tu/stub redis-console/handler)]
      (is (= (tu/get-called?) false))
      (console/app-handler {:broker       tu/redis-producer
                            :app-name     ""
                            :route-prefix "/goose/console"}
                           (mock/request :get "/goose/console/"))
      (is (= (tu/get-called?) true)))))

(deftest handler-test
  (let [req-with-client-opts (assoc (mock/request :get "foo/")
                               :client-opts {:broker       tu/redis-producer
                                             :app-name     ""
                                             :route-prefix "foo"})]
    (testing "Should call redis-handler given redis-broker"
      (with-redefs [redis-console/handler (tu/stub redis-console/handler)]
        (is (= (tu/get-called?) false))
        (broker/handler tu/redis-producer req-with-client-opts)
        (is (= (tu/get-called?) true))))
    (testing "Should throw AbstractMethodError exception given rmq-broker"
      (is (thrown? AbstractMethodError (broker/handler tu/rmq-producer req-with-client-opts))))
    (testing "Should throw IllegalArgumentException given invalid broker"
      (is (thrown? IllegalArgumentException (broker/handler {:broker :invalid-broker} req-with-client-opts))))))
