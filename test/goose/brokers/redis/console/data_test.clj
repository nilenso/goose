(ns goose.brokers.redis.console.data-test
  (:require [clojure.test :refer [deftest is testing]]
            [goose.brokers.redis.console.data :as console]
            [goose.defaults :as d]
            [goose.factories :as f]
            [goose.test-utils :as tu]))

(deftest jobs-size-test
  (testing "Should return job size for enqueued, scheduled, periodic and dead jobs"
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 0 :scheduled 0
                                              :periodic 0 :dead 0}))
    (f/create-jobs {:enqueued 2 :scheduled 3 :periodic 2 :dead 3})
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 2 :scheduled 3
                                              :periodic 2 :dead 3})))
  (tu/clear-redis)
  (testing "Should return jobs size given jobs exist in multiple queues"
    (f/create-jobs {:enqueued 3 :scheduled 3 :periodic 1 :dead 1}
                   {:enqueued {:queue       "queue1"
                               :ready-queue "goose/queue:queue1"}})
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 3 :scheduled 3 :periodic 1 :dead 1}))))

(deftest enqueued-page-data-test
  (testing "Should get enqueued-jobs page data i.e :total-jobs, total-jobs count, all queues, current queue and page"
    (f/create-jobs {:enqueued 2})
    (let [{:keys [queues page queue jobs total-jobs]} (console/enqueued-page-data tu/redis-conn tu/queue "1")]
      (is (= [tu/queue] queues))
      (is (= 1 page))
      (is (= tu/queue queue))
      (is (= 2 (count jobs)))
      (is (= 2 total-jobs))))
  (testing "Should default page value to 1 given no page value"
    (is (= 1 (:page (console/enqueued-page-data tu/redis-conn tu/queue nil)))))
  (tu/clear-redis)
  (testing "Should get at-max page-size jobs given >page-size jobs in redis"
    (f/create-jobs {:enqueued 8})
    (with-redefs [d/page-size 3]
      (let [{:keys [page jobs total-jobs]} (console/enqueued-page-data tu/redis-conn tu/queue "2")]
        (is (= 2 page))
        (is (= 3 (count jobs)))
        (is (= 8 total-jobs)))
      (is (= 2 (-> (console/enqueued-page-data tu/redis-conn tu/queue "3") (get :jobs) count)))))
  (tu/clear-redis)
  (testing "Should get name of all the queues"
    (f/create-async-job)
    (f/create-async-job {:queue       "queue1"
                         :ready-queue "goose/queue:queue1"})
    (is (true? (every? #{tu/queue "queue1"} (-> (console/enqueued-page-data tu/redis-conn tu/queue nil) (get :queues))))))
  (tu/clear-redis)
  (testing "Should get no jobs data given no jobs exist in redis"
    (let [{:keys [jobs total-jobs]} (console/enqueued-page-data tu/redis-conn tu/queue nil)]
      (is (= [] jobs))
      (is (= 0 total-jobs)))))
