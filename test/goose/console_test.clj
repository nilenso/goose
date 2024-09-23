(ns goose.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.broker :as broker]
            [goose.brokers.redis.console :as redis-console]
            [goose.brokers.rmq.console :as rmq-console]
            [goose.console :as console]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock]
            [spy.core :as spy])
  (:import (java.lang IllegalArgumentException)))

(use-fixtures :each tu/redis-fixture)

(deftest app-handler-test
  (testing "Should append query-params to request, when url contains query parameters"
    (with-redefs [redis-console/handler (fn [_ req] (:query-params req))]
      (is (= {"page" "2"} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue?page=2"))))
      (is (= {"foo" "bar"} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue?foo=bar"))))
      (is (= {} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue"))))))
  (testing "Should keywordize the params given a request"
    (with-redefs [redis-console/handler (fn [_ req] (:params req))]
      (is (= {:page "2"} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue?page=2"))))
      (is (= {:foo "bar"} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue?foo=bar"))))
      (is (= {} (console/app-handler tu/redis-console-opts (mock/request :get "/enqueue"))))))
  (testing "Should override request-method if form field contains _method"
    (with-redefs [redis-console/handler (fn [_ req] (:request-method req))]
      (is (= :delete (console/app-handler tu/redis-console-opts (mock/request :post "/enqueue/queue/default"
                                                                              {"_method" "delete" "queue" "default"}))))
      (is (= :patch (console/app-handler tu/redis-console-opts (mock/request :post "/enqueue/queue/default"
                                                                             {"_method" "patch"})))))))

(deftest dispatch-handler-test
  (testing "Should dispatch to redis handler when app-handler is given redis broker details"
    (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
      (is (true? (spy/not-called? redis-console/handler)))
      (console/app-handler {:broker       tu/redis-producer
                            :app-name     ""
                            :route-prefix "/goose/console"}
                           (mock/request :get "/goose/console/"))
      (is (true? (spy/called? redis-console/handler)))))
  (testing "Should dispatch to rmq handler when app-handler is given rmq broker details"
    (with-redefs [rmq-console/handler (spy/spy rmq-console/handler)]
      (is (true? (spy/not-called? redis-console/handler)))
      (console/app-handler {:broker       tu/rmq-producer
                            :app-name     ""
                            :route-prefix "/goose/console"}
                           (mock/request :get "/goose/console/"))
      (is (true? (spy/called? rmq-console/handler))))))

(def dead-fn-atom (atom 0))
(defn dead-fn
  [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest handler-test
  (let [req-with-client-opts (assoc (mock/request :get "foo/")
                                    :console-opts {:broker       tu/redis-producer
                                                   :app-name     ""
                                                   :route-prefix "foo"}
                                    :prefix-route (partial str "foo"))]
    (testing "Should call redis-handler given redis-broker to broker/handler"
      (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
        (is (true? (spy/not-called? redis-console/handler)))
        (broker/handler tu/redis-producer req-with-client-opts)
        (is (true? (spy/called? redis-console/handler)))))
    (testing "Should call rmq handler given rmq-broker to broker/handler"
      (with-redefs [rmq-console/handler (spy/spy rmq-console/handler)]
        (is (true? (spy/not-called? rmq-console/handler)))
        (broker/handler tu/rmq-producer (assoc-in req-with-client-opts
                                                  [:console-opts
                                                   :broker] tu/rmq-producer))
        (is (true? (spy/called? rmq-console/handler)))))
    (testing "Should throw IllegalArgumentException given invalid broker"
      (is (thrown? IllegalArgumentException (broker/handler {:broker :invalid-broker} req-with-client-opts))))))
