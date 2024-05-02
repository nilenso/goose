(ns goose.brokers.redis.console.page-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console :as console]
            [goose.brokers.redis.console.pages.enqueued :as enqueued]
            [goose.brokers.redis.console.pages.home :as home]
            [goose.defaults :as d]
            [goose.factories :as f]
            [goose.test-utils :as tu]
            [goose.utils :as u]
            [ring.mock.request :as mock]
            [spy.core :as spy]))

(use-fixtures :each tu/redis-fixture)

(deftest enqueued-jobs-validations-test
  (testing "Should set req params to default values if values do not conform specs"
    (let [random-id (str (random-uuid))]
      (is (= 1 (:page (enqueued/validate-get-jobs {}))))
      (is (= 1 (:page (enqueued/validate-get-jobs {:page nil}))))
      (is (= 2 (:page (enqueued/validate-get-jobs {:page "2"}))))
      (is (= 1 (:page (enqueued/validate-get-jobs {:page "two"}))))
      (is (= 1 (:page (enqueued/validate-get-jobs {:page "2w"}))))

      (is (= nil (:queue (enqueued/validate-get-jobs {}))))
      (is (= nil (:queue (enqueued/validate-get-jobs {:queue nil}))))
      (is (= "queue" (:queue (enqueued/validate-get-jobs {:queue "queue"}))))
      (is (= nil (:queue (enqueued/validate-get-jobs {:queue :queue}))))

      (let [valid-filter-type ["id" "execute-fn-sym" "type"]
            random-filter-type (rand-nth ["id" "execute-fn-sym" "type"])]
        (is (some #(= % (:filter-type (enqueued/validate-get-jobs {:filter-type  random-filter-type
                                                                   :filter-value ""})))
                  valid-filter-type)))

      (is (= random-id (:filter-value (enqueued/validate-get-jobs {:filter-type  "id"
                                                                   :filter-value random-id}))))
      (is (nil? (:filter-value (enqueued/validate-get-jobs {:filter-type  "id"
                                                            :filter-value (rand-nth ["abcd" ""])}))))

      (is (= "some-namespace/fn-name" (:filter-value (enqueued/validate-get-jobs {:filter-type  "execute-fn-sym"
                                                                                  :filter-value "some-namespace/fn-name"}))))
      (is (nil? (:filter-value (enqueued/validate-get-jobs {:filter-type  "execute-fn-sym"
                                                            :filter-value (rand-nth [123 nil])}))))

      (let [valid-type ["failed" "unexecuted"]
            random-valid-type (rand-nth ["failed" "unexecuted"])]
        (is (some #(= % (:filter-value (enqueued/validate-get-jobs {:filter-type  "type"
                                                                    :filter-value random-valid-type})))
                  valid-type)))
      (is (nil? (:filter-value (enqueued/validate-get-jobs {:filter-type  "type"
                                                            :filter-value (rand-nth [true false "retried"])}))))

      (is (= 3 (:limit (enqueued/validate-get-jobs {:limit "3"}))))
      (is (= d/limit (:limit (enqueued/validate-get-jobs {:limit (rand-nth ["21w" "one" :1])})))))))

(deftest jobs-validation-test
  (testing "Should not modify jobs given sequential jobs"
    (is (= ["encoded-job-1" "encoded-job-2"] (enqueued/validate-jobs ["encoded-job-1" "encoded-job-2"]))))
  (testing "Should convert to sequential structure given non-seq jobs"
    (is (= ["encoded-job-1"] (enqueued/validate-jobs "encoded-job-1")))))

(deftest page-handler-test
  (testing "Main handler should invoke home-page handler"
    (with-redefs [home/page (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get ""))
      (true? (spy/called-once? home/page))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses home/page)))))

  (testing "Main Handler should invoke enqueued-page handler"
    (with-redefs [enqueued/get-jobs (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued"))
      (true? (spy/called-once? enqueued/get-jobs))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/get-jobs))))
    (with-redefs [enqueued/get-jobs (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued/queue/default"))
      (true? (spy/called-once? enqueued/get-jobs))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/get-jobs)))
      (is (= "default" (get-in (first (spy/first-call enqueued/get-jobs)) [:params :queue])))))

  (testing "Main handler should invoke purge-queue handler"
    (with-redefs [enqueued/purge-queue (spy/stub {:status 302 :headers {"Location" "/enqueued"} :body ""})]
      (console/handler tu/redis-producer (mock/request :delete "/enqueued/queue/default"))
      (true? (spy/called-once? enqueued/purge-queue))
      (is (= [{:status 302 :headers {"Location" "/enqueued"} :body ""}] (spy/responses enqueued/purge-queue)))))

  (testing "Main handler should invoke delete-jobs handler"
    (with-redefs [enqueued/delete-jobs (spy/stub {:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""})]
      (console/handler tu/redis-producer (mock/request :delete "/enqueued/queue/default/jobs"))
      (true? (spy/called-once? enqueued/delete-jobs))
      (is (= [{:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""}] (spy/responses enqueued/delete-jobs)))))

  (testing "Main handler should invoke prioritise-jobs handler"
    (with-redefs [enqueued/prioritise-jobs (spy/stub {:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""})]
      (console/handler tu/redis-producer (mock/request :post "/enqueued/queue/default/jobs"))
      (true? (spy/called-once? enqueued/prioritise-jobs))
      (is (= [{:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""}] (spy/responses enqueued/prioritise-jobs))))))

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

(deftest delete-jobs-test
  (testing "Should delete 1 job given its encoded form in req params"
    (f/create-jobs {:enqueued 3})
    (let [[first-job] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)
          encoded-job (u/encode-to-str first-job)]
      (is (= 3 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} (enqueued/delete-jobs {:console-opts tu/redis-console-opts
                                                   :params       {:queue tu/queue
                                                                  :jobs  encoded-job}
                                                   :prefix-route str})))
      (is (= 2 (enqueued-jobs/size tu/redis-conn tu/queue)))))
  (tu/clear-redis)
  (testing "Should delete more than 1 job given encoded job/s"
    (f/create-jobs {:enqueued 4})
    (let [first-2-jobs (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 1)
          encoded-2-jobs (mapv u/encode-to-str first-2-jobs)
          response (enqueued/delete-jobs {:console-opts tu/redis-console-opts
                                          :params       {:queue tu/queue
                                                         :jobs  encoded-2-jobs}
                                          :prefix-route str})]
      (is (= 2 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} response)))))

(deftest prioritise-jobs-test
  (testing "Should prioritise 1 job given its encoded form in params job/s"
    (f/create-jobs {:enqueued 3})
    (let [[j1 j2 _] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 2)
          encoded-job (u/encode-to-str j2)]
      (is (= (list j1) (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)))
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} (enqueued/prioritise-jobs {:console-opts tu/redis-console-opts
                                                       :params       {:queue tu/queue
                                                                      :jobs  encoded-job}
                                                       :prefix-route str})))
      (is (= (list j2) (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)))))
  (tu/clear-redis)
  (testing "Should delete more than 1 job given encoded job/s"
    (f/create-jobs {:enqueued 4})
    (let [[j1 j2 j3 j4] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 3)
          encoded-2-jobs (mapv u/encode-to-str [j4 j3])]
      (is (= [j1 j2] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 1)))
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} (enqueued/prioritise-jobs {:console-opts tu/redis-console-opts
                                                       :params       {:queue tu/queue
                                                                      :jobs  encoded-2-jobs}
                                                       :prefix-route str})))
      (is (= [j3 j4] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 1))))))
