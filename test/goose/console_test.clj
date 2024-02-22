(ns goose.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.broker :as broker]
            [goose.brokers.redis.console :as redis-console]
            [goose.console :as console]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock]
            [spy.core :as spy])
  (:import (java.lang AbstractMethodError IllegalArgumentException)))

(use-fixtures :each tu/redis-fixture)

(deftest dispatch-handler-test
  (testing "Should dispatch to redis handler when app-handler is given redis broker details"
    (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
      (is (true? (spy/not-called? redis-console/handler)))
      (console/app-handler {:broker       tu/redis-producer
                            :app-name     ""
                            :route-prefix "/goose/console"}
                           (mock/request :get "/goose/console/"))
      (is (true? (spy/called? redis-console/handler))))))

(deftest handler-test
  (let [req-with-client-opts (assoc (mock/request :get "foo/")
                               :client-opts {:broker       tu/redis-producer
                                             :app-name     ""
                                             :route-prefix "foo"})]
    (testing "Should call redis-handler given redis-broker"
      (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
        (is (true? (spy/not-called? redis-console/handler)))
        (broker/handler tu/redis-producer req-with-client-opts)
        (is (true? (spy/called? redis-console/handler)))))
    (testing "Should throw AbstractMethodError exception given rmq-broker"
      (is (thrown? AbstractMethodError (broker/handler tu/rmq-producer req-with-client-opts))))
    (testing "Should throw IllegalArgumentException given invalid broker"
      (is (thrown? IllegalArgumentException (broker/handler {:broker :invalid-broker} req-with-client-opts))))))
