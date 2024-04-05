(ns goose.brokers.redis.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.console :as redis-console]
            [goose.defaults :as d]
            [goose.factories :as f]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock]
            [spy.core :as spy])
  (:import (java.io File)))

(use-fixtures :each tu/redis-fixture)

(deftest jobs-size-test
  (testing "Should return job size for enqueued, scheduled, periodic and dead jobs"
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 0 :scheduled 0
                                                    :periodic 0 :dead 0}))
    (f/create-jobs {:enqueued 2 :scheduled 3 :periodic 2 :dead 3})
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 2 :scheduled 3
                                                    :periodic 2 :dead 3})))
  (tu/clear-redis)
  (testing "Should return jobs size given jobs exist in multiple queues"
    (f/create-jobs {:enqueued 3 :scheduled 3 :periodic 1 :dead 1}
                   {:enqueued {:queue       "queue1"
                               :ready-queue "goose/queue:queue1"}})
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 3 :scheduled 3 :periodic 1 :dead 1}))))

(deftest enqueued-page-data-test
  (testing "Should get enqueued-jobs page data i.e :total-jobs, total-jobs count, all queues, current queue and page"
    (f/create-jobs {:enqueued 2})
    (let [{:keys [queues page queue jobs total-jobs]} (redis-console/enqueued-page-data tu/redis-conn tu/queue "1")]
      (is (= [tu/queue] queues))
      (is (= 1 page))
      (is (= tu/queue queue))
      (is (= 2 (count jobs)))
      (is (= 2 total-jobs))))
  (testing "Should default page value to 1 given no page value"
    (is (= 1 (:page (redis-console/enqueued-page-data tu/redis-conn tu/queue nil)))))
  (tu/clear-redis)
  (testing "Should get at-max page-size jobs given >page-size jobs in redis"
    (f/create-jobs {:enqueued 8})
    (with-redefs [d/page-size 3]
      (let [{:keys [page jobs total-jobs]} (redis-console/enqueued-page-data tu/redis-conn tu/queue "2")]
        (is (= 2 page))
        (is (= 3 (count jobs)))
        (is (= 8 total-jobs)))
      (is (= 2 (-> (redis-console/enqueued-page-data tu/redis-conn tu/queue "3") (get :jobs) count)))))
  (tu/clear-redis)
  (testing "Should get name of all the queues"
    (f/create-async-job)
    (f/create-async-job {:queue       "queue1"
                         :ready-queue "goose/queue:queue1"})
    (is (= [tu/queue "queue1"] (-> (redis-console/enqueued-page-data tu/redis-conn tu/queue nil) (get :queues)))))
  (tu/clear-redis)
  (testing "Should get no jobs data given no jobs exist in redis"
    (let [{:keys [jobs total-jobs]} (redis-console/enqueued-page-data tu/redis-conn tu/queue nil)]
      (is (= [] jobs))
      (is (= 0 total-jobs)))))

(deftest page-handler-test
  (testing "Main handler should invoke home-page handler"
    (with-redefs [redis-console/home-page (spy/stub {:status 200 :body "Mocked resp"})]
      (redis-console/handler tu/redis-producer (mock/request :get ""))
      (true? (spy/called-once? redis-console/home-page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses redis-console/home-page)))))
  (testing "Main Handler should invoke enqueued-page handler"
    (with-redefs [redis-console/enqueued-page (spy/stub {:status 200 :body "Mocked resp"})]
      (redis-console/handler tu/redis-producer (mock/request :get "/enqueued"))
      (true? (spy/called-once? redis-console/enqueued-page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses redis-console/enqueued-page))))
    (with-redefs [redis-console/enqueued-page (spy/stub {:status 200 :body "Mocked resp"})]
      (redis-console/handler tu/redis-producer (mock/request :get "/enqueued/queue/default"))
      (true? (spy/called-once? redis-console/enqueued-page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses redis-console/enqueued-page)))
      (is (= "default" (get-in (first (spy/first-call redis-console/enqueued-page)) [:route-params :queue]))))))

(deftest handler-test
  (testing "Should serve css file on GET request at /css/style.css route"
    (let [response (redis-console/handler tu/redis-producer (-> (mock/request :get "goose/console/css/style.css")
                                                                (assoc :console-opts {:broker       tu/redis-producer
                                                                                      :app-name     ""
                                                                                      :route-prefix "goose/console"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "text/css"))))
  (testing "Should serve goose logo on GET request at /img/goose-logo.png route"
    (let [response (redis-console/handler tu/redis-producer (-> (mock/request :get "foo/img/goose-logo.png")
                                                                (assoc :console-opts {:broker       tu/redis-producer
                                                                                      :app-name     ""
                                                                                      :route-prefix "foo"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "image/png"))))
  (testing "Should redirect to main route with slash (goose/console/) on GET req to route without slash(goose/console)"
    (is (= (redis-console/handler tu/redis-producer (-> (mock/request :get "foo")
                                                        (assoc :console-opts {:broker       tu/redis-producer
                                                                              :app-name     ""
                                                                              :route-prefix "foo"}
                                                               :prefix-route (partial str "foo"))))
           {:status  302
            :headers {"Location" "foo/"}
            :body    ""})))
  (testing "Should show not-found page given invalid route"
    (is (= (redis-console/handler tu/redis-producer (-> (mock/request :get "foo/invalid")
                                                        (assoc :console-opts {:broker       tu/redis-producer
                                                                              :app-name     ""
                                                                              :route-prefix "foo"})))
           {:body    "<div> Not found </div>"
            :headers {}
            :status  404}))))
