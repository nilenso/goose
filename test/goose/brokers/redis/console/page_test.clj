(ns goose.brokers.redis.console.page-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console :as console]
            [goose.brokers.redis.console.pages.enqueued :as enqueued]
            [goose.brokers.redis.console.pages.home :as home]
            [goose.factories :as f]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock]
            [spy.core :as spy]))

(use-fixtures :each tu/redis-fixture)

(deftest purge-queue-test
  (testing "Should purge a queue"
    (f/create-async-job)
    (f/create-async-job {:queue       "queue1"
                         :ready-queue "goose/queue:queue1"})
    (is (true? (every? #{tu/queue "queue1"} (enqueued-jobs/list-all-queues tu/redis-conn))))
    (is (= 2 (count (enqueued-jobs/list-all-queues tu/redis-conn))))
    (is (= {:body    ""
            :headers {"Location" "/enqueued"}
            :status  302} (enqueued/purge-queue {:console-opts tu/redis-console-opts
                                                 :params       {:queue tu/queue}
                                                 :prefix-route str})))
    (is (= ["queue1"] (enqueued-jobs/list-all-queues tu/redis-conn)))))

(deftest page-handler-test
  (testing "Main handler should invoke home-page handler"
    (with-redefs [home/page (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get ""))
      (true? (spy/called-once? home/page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses home/page)))))
  (testing "Main Handler should invoke enqueued-page handler"
    (with-redefs [enqueued/page (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued"))
      (true? (spy/called-once? enqueued/page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/page))))
    (with-redefs [enqueued/page (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued/queue/default"))
      (true? (spy/called-once? enqueued/page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/page)))
      (is (= "default" (get-in (first (spy/first-call enqueued/page)) [:route-params :queue]))))
    (with-redefs [enqueued/purge-queue (spy/stub {:status 302 :headers {"Location" "/enqueued"} :body ""})]
      (console/handler tu/redis-producer (mock/request :delete "/enqueued/queue/default"))
      (true? (spy/called-once? enqueued/purge-queue))
      (is (= [{:status 302 :headers {"Location" "/enqueued"} :body ""}] (spy/responses enqueued/purge-queue))))))
