(ns goose.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.broker :as broker]
            [goose.brokers.redis.broker :as redis]
            [goose.brokers.redis.console :as redis-console]
            [goose.client :as c]
            [goose.console :as console]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock]
            [spy.core :as spy]
            [goose.worker :as w]
            [hickory.core :as h]
            [hickory.select :as h-select])
  (:import (java.lang AbstractMethodError IllegalArgumentException)))

(use-fixtures :each tu/redis-fixture)

(deftest app-handler-test
  (let [client-opts {:app-name     ""
                     :route-prefix ""
                     :broker       (redis/new-producer redis/default-opts)}]
    (testing "Should append query-params to request, when url contains query parameters"
      (with-redefs [redis-console/handler (fn [_ req] (:query-params req))]
        (is (= {"page" "2"} (console/app-handler client-opts (mock/request :get "/enqueue?page=2"))))
        (is (= {"foo" "bar"} (console/app-handler client-opts (mock/request :get "/enqueue?foo=bar"))))
        (is (= {} (console/app-handler client-opts (mock/request :get "/enqueue"))))))
    (testing "Should keywordize the params given a request"
      (with-redefs [redis-console/handler (fn [_ req] (:params req))]
        (is (= {:page "2"} (console/app-handler client-opts (mock/request :get "/enqueue?page=2"))))
        (is (= {:foo "bar"} (console/app-handler client-opts (mock/request :get "/enqueue?foo=bar"))))
        (is (= {} (console/app-handler client-opts (mock/request :get "/enqueue"))))))))

(deftest dispatch-handler-test
  (testing "Should dispatch to redis handler when app-handler is given redis broker details"
    (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
      (is (true? (spy/not-called? redis-console/handler)))
      (console/app-handler {:broker       tu/redis-producer
                            :app-name     ""
                            :route-prefix "/goose/console"}
                           (mock/request :get "/goose/console/"))
      (is (true? (spy/called? redis-console/handler))))))

(def dead-fn-atom (atom 0))
(defn dead-fn
  [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest homepage-view-test
  (testing "Stats bar view should have should have 2 enqueued, 1 scheduled, 1 periodic and 1 dead job"

    ;;Simulate failed job
    (let [worker (w/start tu/redis-worker-opts)
          _ (c/perform-async (assoc tu/redis-client-opts :retry-opts (assoc tu/retry-opts :max-retries 0))
                             `dead-fn 10) 
          circuit-breaker (atom 0)]
      (while (and (< @circuit-breaker 1) (not= @dead-fn-atom 1))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker))

    (c/perform-async tu/redis-client-opts `tu/my-fn 1)
    (c/perform-async tu/redis-client-opts `tu/my-fn 2)

    (c/perform-in-sec tu/redis-client-opts 10000 `tu/my-fn 1)

    (c/perform-every tu/redis-client-opts {:cron-name     "my-cron-entry"
                                           :cron-schedule "* * * * *"
                                           :timezone      "US/Pacific"} `tu/my-fn :foo)
    (let [homepage-stringified-html (:body (console/app-handler {:broker       tu/redis-producer
                                                                 :app-name     ""
                                                                 :route-prefix "/goose/console"}
                                                                (mock/request :get "/goose/console/")))
          homepage (h/as-hickory (h/parse homepage-stringified-html))
          extract-job-size (fn [job-type]
                             (let [selection (h-select/select (h-select/child (h-select/id job-type)
                                                                              (h-select/class "number")) homepage)]
                               (-> selection first :content first Integer/parseInt)))]
      (is (= (extract-job-size "enqueued") 2))
      (is (= (extract-job-size "scheduled") 1))
      (is (= (extract-job-size "periodic") 1))
      (is (= (extract-job-size "dead") 1)))))

(deftest handler-test
  (let [req-with-client-opts (assoc (mock/request :get "foo/")
                               :console-opts {:broker       tu/redis-producer
                                              :app-name     ""
                                              :route-prefix "foo"}
                               :prefix-route (partial str "foo"))]
    (testing "Should call redis-handler given redis-broker"
      (with-redefs [redis-console/handler (spy/spy redis-console/handler)]
        (is (true? (spy/not-called? redis-console/handler)))
        (broker/handler tu/redis-producer req-with-client-opts)
        (is (true? (spy/called? redis-console/handler)))))
    (testing "Should throw AbstractMethodError exception given rmq-broker"
      (is (thrown? AbstractMethodError (broker/handler tu/rmq-producer req-with-client-opts))))
    (testing "Should throw IllegalArgumentException given invalid broker"
      (is (thrown? IllegalArgumentException (broker/handler {:broker :invalid-broker} req-with-client-opts))))))
