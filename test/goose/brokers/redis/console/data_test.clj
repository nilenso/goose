(ns goose.brokers.redis.console.data-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.console.data :as console]
            [goose.defaults :as d]
            [goose.factories :as f]
            [goose.test-utils :as tu]))

(use-fixtures :each tu/redis-fixture)

(deftest jobs-size-test
  (testing "Should return job size for enqueued, scheduled, cron and dead jobs"
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 0 :scheduled 0
                                              :cron     0 :dead 0}))
    (f/create-jobs-in-redis {:enqueued 2 :scheduled 3 :cron 2 :dead 3})
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 2 :scheduled 3
                                              :cron     2 :dead 3})))
  (tu/clear-redis)
  (testing "Should return jobs size given jobs exist in multiple queues"
    (f/create-jobs-in-redis {:enqueued 3 :scheduled 3 :cron 1 :dead 1}
                            {:enqueued {:queue       "queue1"
                                        :ready-queue "goose/queue:queue1"}})
    (is (= (console/jobs-size tu/redis-conn) {:enqueued 3 :scheduled 3 :cron 1 :dead 1}))))

(deftest enqueued-page-data-test
  (testing "Should get enqueued-jobs page data i.e all jobs, total-jobs count, all queues, current queue and page"
    (f/create-jobs-in-redis {:enqueued 2})
    (let [{:keys [queues page queue jobs total-jobs]} (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                                                                 :page  1})]
      (is (= [tu/queue] queues))
      (is (= 1 page))
      (is (= tu/queue queue))
      (is (= 2 (count jobs)))
      (is (= 2 total-jobs))))
  (tu/clear-redis)
  (testing "Should get at-max page-size jobs given larger page-size"
    (f/create-jobs-in-redis {:enqueued 8})
    (with-redefs [d/page-size 3]
      (let [{:keys [page jobs total-jobs]} (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                                                      :page  2})]
        (is (= 2 page))
        (is (= 3 (count jobs)))
        (is (= 8 total-jobs)))
      (is (= 2 (-> (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                              :page  3}) (get :jobs) count)))))
  (tu/clear-redis)
  (testing "Should get name of all the queues"
    (f/create-async-job-in-redis)
    (f/create-async-job-in-redis {:queue       "queue1"
                                  :ready-queue "goose/queue:queue1"})
    (is (every? #{tu/queue "queue1"} (-> (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                                                    :page  1})
                                         (get :queues)))))
  (tu/clear-redis)
  (testing "Should get no jobs data given no jobs exist in redis"
    (is (= {:queues '()
            :page   1
            :queue  tu/queue
            :jobs   []} (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                                   :page  1}))))
  (testing "Should return no jobs given valid filter params but jobs exist in redis"
    (is (= {:queues '()
            :page   1
            :queue  tu/queue
            :jobs   []} (console/enqueued-page-data tu/redis-conn {:queue         tu/queue
                                                                   :page          1
                                                                   :filter-type   "id"
                                                                   :filter-params (str (random-uuid))
                                                                   :limit         10}))))
  (tu/clear-redis)
  (testing "Should filter the jobs by id, execute-fn-symbol and type, given valid filter-params"
    (f/create-jobs-in-redis {:enqueued 3})
    (f/create-jobs-in-redis {:enqueued 4} {:enqueued {:execute-fn-sym `prn}})
    (f/create-jobs-in-redis {:enqueued 2} {:enqueued {:state {:error           "Error"
                                                              :last-retried-at 1701344365468,
                                                              :first-failed-at 1701344365468,
                                                              :retry-count     2,
                                                              :retry-at        1701344433359}}})
    (let [job1 (-> (enqueued-jobs/get-by-range tu/redis-conn tu/queue 1 10) first)
          base-params {:queue  tu/queue
                       :queues (list tu/queue)
                       :page   1
                       :limit  10}]
      (is (= {:page   1
              :queue  tu/queue
              :queues (list tu/queue)
              :jobs   [job1]} (console/enqueued-page-data tu/redis-conn (merge base-params {:filter-type  "id"
                                                                                            :filter-value (:id job1)}))))
      (is (= 4 (-> (console/enqueued-page-data tu/redis-conn (merge base-params {:filter-type  "execute-fn-sym"
                                                                                 :filter-value "clojure.core/prn"}))
                   :jobs count)))
      (is (= 2 (-> (console/enqueued-page-data tu/redis-conn (merge base-params {:filter-type  "type"
                                                                                 :filter-value "failed"}))
                   :jobs count)))
      (is (= 7 (-> (console/enqueued-page-data tu/redis-conn (merge base-params {:filter-type  "type"
                                                                                 :filter-value "unexecuted"}))
                   :jobs count)))))
  (tu/clear-redis)
  (testing "Should not return jobs given invalid params"
    (f/create-jobs-in-redis {:enqueued 1})
    (is (= {:queue  tu/queue
            :page   1
            :queues (list tu/queue)
            :jobs   []} (console/enqueued-page-data tu/redis-conn {:queue        tu/queue
                                                                   :page         1
                                                                   :filter-type  "id"
                                                                   :filter-value nil
                                                                   :limit        10}))))
  (tu/clear-redis)
  (testing "Should return all the jobs given no filter params"
    (f/create-jobs-in-redis {:enqueued 2})
    (let [jobs (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 2)]
      (is (= {:queue      tu/queue
              :page       1
              :queues     (list tu/queue)
              :total-jobs 2
              :jobs       jobs} (console/enqueued-page-data tu/redis-conn {:queue tu/queue
                                                                           :page  1}))))))

(deftest dead-page-data-test
  (testing "Should get dead-jobs page data i.e jobs, total-jobs and page"
    (f/create-jobs-in-redis {:dead 9})
    (let [jobs (dead-jobs/get-by-range tu/redis-conn 0 9)]
      (is (= {:jobs       jobs
              :total-jobs 9
              :page       1} (console/dead-page-data tu/redis-conn {:page 1})))))
  (tu/clear-redis)
  (testing "Should return no jobs given no jobs exist in redis"
    (let [result (console/dead-page-data tu/redis-conn {:page 1})]
      (is (= {:page       1
              :jobs       []
              :total-jobs 0} result))))
  (testing "Should return no jobs given valid filter-params but no jobs in redis"
    (is (= {:page 1
            :jobs []} (console/dead-page-data tu/redis-conn {:page         1
                                                             :filter-type  "id"
                                                             :filter-value (str (random-uuid))
                                                             :limit        10})))
    (is (= {:page 1
            :jobs []} (console/dead-page-data tu/redis-conn {:page         1
                                                             :filter-type  "execute-fn-sym"
                                                             :filter-value "non-existent"
                                                             :limit        10})))
    (is (= {:page 1
            :jobs []} (console/dead-page-data tu/redis-conn {:page         1
                                                             :filter-type  "queue"
                                                             :filter-value "random-queue"
                                                             :limit        10}))))
  (testing "Should filter based on filter-type"
    (f/create-jobs-in-redis {:dead 7})
    (let [random-job (rand-nth (dead-jobs/get-by-range tu/redis-conn 0 7))
          result (console/dead-page-data tu/redis-conn {:page         1
                                                        :filter-type  "id"
                                                        :filter-value (:id random-job)})]
      (is (= {:page 1
              :jobs [random-job]} result)))

    (let [jobs (dead-jobs/get-by-range tu/redis-conn 0 9)
          {filtered-jobs :jobs} (console/dead-page-data tu/redis-conn {:page         1
                                                                       :filter-type  "execute-fn-sym"
                                                                       :filter-value "goose.test-utils/my-fn"
                                                                       :limit        9})]
      (is (set filtered-jobs) (set jobs)))
    (let [jobs (dead-jobs/get-by-range tu/redis-conn 0 9)
          {filtered-jobs :jobs} (console/dead-page-data tu/redis-conn {:page         1
                                                                       :filter-type  "queue"
                                                                       :filter-value tu/queue
                                                                       :limit        9})]
      (is (= (set filtered-jobs) (set jobs)))))
  (tu/clear-redis)
  (testing "Should return all the jobs in page 2"
    (f/create-jobs-in-redis {:dead 12})
    (let [jobs (dead-jobs/get-by-range tu/redis-conn 10 19)
          {page-2-jobs :jobs} (console/dead-page-data tu/redis-conn {:page 2})]
      (is (= page-2-jobs jobs))
      (is (= 2 (count page-2-jobs)))))
  (tu/clear-redis)
  (testing "Should return no jobs if the filter is not satisfied with any jobs"
    (f/create-jobs-in-redis {:dead 6})
    (let [result (console/dead-page-data tu/redis-conn {:page         1
                                                        :filter-type  "queue"
                                                        :filter-value "some-random-queue"
                                                        :limit        10})]
      (is (= {:page 1
              :jobs []} result))))
  (tu/clear-redis)
  (testing "Should return no jobs given invalid filter params"
    (let [result (console/dead-page-data tu/redis-conn {:page         1
                                                        :filter-type  "invalid-filter"
                                                        :filter-value "some-invalid-filter"})]
      (is (= {:page 1
              :jobs nil} result)))))

(deftest scheduled-page-data-test
  (testing "Should get scheduled-jobs page data i.e jobs, total-jobs and page"
    (f/create-jobs-in-redis {:scheduled 12})
    (let [jobs (scheduled-jobs/get-by-range tu/redis-conn 0 9)]
      (is (= {:jobs       jobs
              :total-jobs 12
              :page       1} (console/scheduled-page-data tu/redis-conn {:page 1})))))
  (tu/clear-redis)
  (testing "Should return no jobs given no jobs exist in redis"
    (is (= {:jobs       []
            :total-jobs 0
            :page       1} (console/scheduled-page-data tu/redis-conn {:page 1}))))
  (testing "Should return no jobs given valid filter-params but no jobs in redis"
    (is (= {:page 1
            :jobs []} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                  :filter-type  "id"
                                                                  :filter-value (str (random-uuid))
                                                                  :limit        10})))
    (is (= {:page 1
            :jobs []} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                  :filter-type  "execute-fn-sym"
                                                                  :filter-value "non-existent"
                                                                  :limit        10})))
    (is (= {:page 1
            :jobs []} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                  :filter-type  "queue"
                                                                  :filter-value "random-queue"
                                                                  :limit        10})))
    (is (= {:page 1
            :jobs []} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                  :filter-type  "type"
                                                                  :filter-value "scheduled"
                                                                  :limit        10}))))
  (testing "Should filter based on filter-type"
    (f/create-jobs-in-redis {:scheduled 7})
    (let [random-job (rand-nth (scheduled-jobs/get-by-range tu/redis-conn 0 7))
          result (console/scheduled-page-data tu/redis-conn {:page         1
                                                             :filter-type  "id"
                                                             :filter-value (:id random-job)})]
      (is (= {:page 1
              :jobs [random-job]} result)))
    (let [jobs (scheduled-jobs/get-by-range tu/redis-conn 0 9)
          {filtered-jobs :jobs} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                            :filter-type  "execute-fn-sym"
                                                                            :filter-value "goose.test-utils/my-fn"
                                                                            :limit        9})]
      (is (= (set filtered-jobs) (set jobs))))
    (let [jobs (scheduled-jobs/get-by-range tu/redis-conn 0 9)
          {filtered-jobs :jobs} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                            :filter-type  "queue"
                                                                            :filter-value tu/queue
                                                                            :limit        9})]
      (is (= (set filtered-jobs) (set jobs))))
    (let [jobs (scheduled-jobs/get-by-range tu/redis-conn 0 9)
          {filtered-jobs :jobs} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                            :filter-type  "type"
                                                                            :filter-value "scheduled"
                                                                            :limit        10})]
      (is (= (set filtered-jobs) (set jobs)))))
  (tu/clear-redis)
  (testing "Should return all the jobs in page 3"
    (f/create-jobs-in-redis {:scheduled 22})
    (is (= {:jobs       (scheduled-jobs/get-by-range tu/redis-conn 20 29)
            :total-jobs 22
            :page       3} (console/scheduled-page-data tu/redis-conn {:page 3}))))
  (tu/clear-redis)
  (testing "Should return no jobs if the filter is not satisfied with any jobs"
    (f/create-jobs-in-redis {:scheduled 7})
    (is (= {:jobs []
            :page 1} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                 :filter-type  "id"
                                                                 :filter-value (str (random-uuid))
                                                                 :limit        10})))
    (is (= {:jobs []
            :page 1} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                 :filter-type  "execute-fn-sym"
                                                                 :filter-value "random-ns"
                                                                 :limit        10})))
    (is (= {:jobs []
            :page 1} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                 :filter-type  "queue"
                                                                 :filter-value "non-exiting-queue"
                                                                 :limit        10})))
    (is (= {:jobs []
            :page 1} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                 :filter-type  "type"
                                                                 :filter-value "failed"
                                                                 :limit        10}))))
  (testing "Should return no jobs given invalid filter params"
    ;; scheduled-page-data takes validated filter types and values, so if either of them is invalid
    ;; their values default to nil
    (is (= {:page 1
            :jobs []} (console/scheduled-page-data tu/redis-conn {:page         1
                                                                  :filter-type  "type"
                                                                  :filter-value nil
                                                                  :limit        10})))))

(deftest cron-page-data-test
  (testing "Should return no jobs given no jobs exist"
    (is (= {:total-jobs 0
            :jobs       []} (console/cron-page-data tu/redis-conn {}))))
  (testing "Should get all cron entries"
    (let [job (f/create-cron-in-redis)
          response (console/cron-page-data tu/redis-conn {})]
      (is (= 1 (:total-jobs response)))
      (is (= [job] (:jobs response)))))
  (tu/clear-redis)
  (testing "Should filter jobs given name"
    (let [_ (f/create-jobs-in-redis {:cron 4})
          foobar-job (f/create-cron-in-redis {:cron-opts {:cron-name "foo-bar"}})
          response (console/cron-page-data tu/redis-conn {:filter-type  "name"
                                                          :filter-value "foo-bar"})]
      (is (= [foobar-job] (:jobs response)))))
  (tu/clear-redis)
  (testing "Should return no jobs given filter does not match any job"
    (let [_ (f/create-jobs-in-redis {:cron 5})
          response (console/cron-page-data tu/redis-conn {:filter-type  "name"
                                                          :filter-value "non-existent-cron-name"})]
      (is (= {:jobs []} response))))
  (testing "Should return no jobs given invalid filter params"
    (is (= {:jobs []} (console/cron-page-data tu/redis-conn {:filter-type  "name"
                                                             :filter-value nil})))))
