(ns goose.brokers.redis.console.page-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console :as console]
            [goose.brokers.redis.console.pages.dead :as dead]
            [goose.brokers.redis.console.pages.enqueued :as enqueued]
            [goose.brokers.redis.console.pages.home :as home]
            [goose.brokers.redis.console.specs :as specs]
            [goose.defaults :as d]
            [goose.factories :as f]
            [goose.test-utils :as tu]
            [goose.utils :as u]
            [ring.mock.request :as mock]
            [spy.core :as spy]))

(use-fixtures :each tu/redis-fixture)

(deftest validate-get-enqueued-jobs-test
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

(deftest validate-get-dead-jobs-test
  (testing "Should set req params to default values if values do not conform specs"
    (let [random-id (str (random-uuid))]
      (is (= 1 (:page (dead/validate-get-jobs {}))))
      (is (= 1 (:page (dead/validate-get-jobs {:page nil}))))
      (is (= 2 (:page (dead/validate-get-jobs {:page "2"}))))
      (is (= 1 (:page (dead/validate-get-jobs {:page "two"}))))
      (is (= 1 (:page (dead/validate-get-jobs {:page "2w"}))))

      (let [valid-filter-type ["id" "execute-fn-sym" "queue"]
            random-filter-type (rand-nth ["id" "execute-fn-sym" "queue"])]
        (is (some #(= % (:filter-type (dead/validate-get-jobs {:filter-type  random-filter-type
                                                               :filter-value ""})))
                  valid-filter-type)))

      (is (= random-id (:filter-value (dead/validate-get-jobs {:filter-type  "id"
                                                               :filter-value random-id}))))
      (is (nil? (:filter-value (dead/validate-get-jobs {:filter-type  "id"
                                                        :filter-value (rand-nth ["abcd" ""])}))))

      (is (= "some-namespace/fn-name" (:filter-value (dead/validate-get-jobs {:filter-type  "execute-fn-sym"
                                                                              :filter-value "some-namespace/fn-name"}))))
      (is (nil? (:filter-value (dead/validate-get-jobs {:filter-type  "execute-fn-sym"
                                                        :filter-value (rand-nth [123 nil])}))))

      (is (= nil (:filter-value (dead/validate-get-jobs {:filter-type  "queue"
                                                         :filter-value :default}))))
      (is (= nil (:filter-value (dead/validate-get-jobs {:filter-type  "queue"
                                                         :filter-value nil}))))
      (is (= "default" (:filter-value (dead/validate-get-jobs {:filter-type  "queue"
                                                               :filter-value "default"}))))
      (is (= nil (:filter-value (dead/validate-get-jobs {:filter-type  "queue"
                                                         :filter-value 123})))))))

(deftest validate-req-params-test
  (testing "Should set req params of job to default value if do not conform spec"
    (let [uuid (str (random-uuid))]
      (is (= uuid (:id (specs/validate-req-params {:id uuid})))))
    (is (nil? (:id (specs/validate-req-params {:id "not-uuid"}))))

    (is (= "valid-queue" (:queue (specs/validate-req-params {:queue "valid-queue"}))))
    (is (nil? (:queue (specs/validate-req-params {:queue :not-string}))))

    (is (= "some-encoded-job" (:encoded-job (specs/validate-req-params
                                              {:job "some-encoded-job"}))))
    (is (nil? (:encoded-job (specs/validate-req-params
                              {:job {:id "123"}}))))

    (is (= ["some-encoded-job"] (:encoded-jobs (specs/validate-req-params
                                                 {:jobs "some-encoded-job"}))))
    (is (= ["some-encoded-job1"
            "some-encoded-job2"] (:encoded-jobs (specs/validate-req-params
                                                  {:jobs ["some-encoded-job1"
                                                          "some-encoded-job2"]}))))))

(deftest page-handler-test
  (testing "Main handler should invoke home-page handler"
    (with-redefs [home/page (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get ""))
      (is (true? (spy/called-once? home/page)))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses home/page)))))

  (testing "Main Handler should invoke get-jobs handler for enqueued jobs page"
    (with-redefs [enqueued/get-jobs (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued"))
      (is (true? (spy/called-once? enqueued/get-jobs)))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/get-jobs))))
    (with-redefs [enqueued/get-jobs (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/redis-producer (mock/request :get "/enqueued/queue/default"))
      (is (true? (spy/called-once? enqueued/get-jobs)))
      (is (= [{:status 200
               :body   "Mocked resp"}] (spy/responses enqueued/get-jobs)))
      (is (= "default" (get-in (first (spy/first-call enqueued/get-jobs)) [:params :queue])))))

  (testing "Main handler should invoke purge-queue handler for enqueued jobs page"
    (with-redefs [enqueued/purge-queue (spy/stub {:status 302 :headers {"Location" "/enqueued"} :body ""})]
      (console/handler tu/redis-producer (mock/request :delete "/enqueued/queue/default"))
      (is (true? (spy/called-once? enqueued/purge-queue)))
      (is (= [{:status 302 :headers {"Location" "/enqueued"} :body ""}] (spy/responses enqueued/purge-queue)))))

  (testing "Main handler should invoke delete-jobs handler for enqueued jobs page"
    (with-redefs [enqueued/delete-jobs (spy/stub {:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""})]
      (console/handler tu/redis-producer (mock/request :delete "/enqueued/queue/default/jobs"))
      (is (true? (spy/called-once? enqueued/delete-jobs)))
      (is (= [{:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""}] (spy/responses enqueued/delete-jobs)))))

  (testing "Main handler should invoke prioritise-jobs handler for enqueued jobs page"
    (with-redefs [enqueued/prioritise-jobs (spy/stub {:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""})]
      (console/handler tu/redis-producer (mock/request :post "/enqueued/queue/default/jobs"))
      (is (true? (spy/called-once? enqueued/prioritise-jobs)))
      (is (= [{:status 302 :headers {"Location" "/enqueued/queue/test"} :body ""}] (spy/responses enqueued/prioritise-jobs)))))

  (testing "Main handler should invoke get-job handler for enqueued jobs page"
    (with-redefs [enqueued/get-job (spy/stub {:status 200
                                              :body   "<html> Enqueue job UI </html>"})]
      (console/handler tu/redis-producer (mock/request
                                           :get (str "/enqueued/queue/default/job/" (random-uuid))))
      (is (true? (spy/called-once? enqueued/get-job)))))

  (testing "Main handler should invoke prioritise job handler for enqueued jobs page"
    (with-redefs [enqueued/prioritise-job (spy/stub {:status  302
                                                     :body    ""
                                                     :headers {"Location" "/enqueued/queue/test"}})]
      (console/handler tu/redis-producer (mock/request
                                           :post (str "/enqueued/queue/default/job/" (random-uuid))))
      (is (true? (spy/called-once? enqueued/prioritise-job)))))

  (testing "Main handler should invoke delete job handler for enqueued jobs page"
    (with-redefs [enqueued/delete-job (spy/stub {:status  302
                                                 :body    ""
                                                 :headers {"Location" "/enqueued/queue/test"}})]
      (console/handler tu/redis-producer (mock/request
                                           :delete (str "/enqueued/queue/default/job/" (random-uuid))))
      (is (true? (spy/called-once? enqueued/delete-job)))))

  (testing "Main handler should invoke get-jobs handler for dead jobs page"
    (with-redefs [dead/get-jobs (spy/stub {:status 200
                                           :body   "<html> Dead jobs page</html>"})]
      (console/handler tu/redis-producer (mock/request :get "/dead"))
      (is (true? (spy/called-once? dead/get-jobs)))))

  (testing "Main handler should invoke purge dead jobs queue"
    (with-redefs [dead/purge-queue (spy/stub {:status  302
                                              :body    ""
                                              :headers {"Location" "/dead"}})]
      (console/handler tu/redis-producer (mock/request :delete "/dead"))
      (is (true? (spy/called-once? dead/purge-queue)))))

  (testing "Main handler should invoke delete dead jobs"
    (with-redefs [dead/delete-jobs (spy/stub {:status 302
                                              :body   ""
                                              :header {"Location" "/dead"}})]
      (console/handler tu/redis-producer (mock/request :delete "/dead/jobs"))
      (is (true? (spy/called-once? dead/delete-jobs)))))

  (testing "Main handler should invoke replay dead jobs"
    (with-redefs [dead/replay-jobs (spy/stub {:status 302
                                              :body   ""
                                              :header {"Location" "/dead"}})]
      (console/handler tu/redis-producer (mock/request :post "/dead/jobs"))
      (is (true? (spy/called-once? dead/replay-jobs)))))

  (testing "Main handler should invoke get dead job"
    (with-redefs [dead/get-job (spy/stub {:status 200 :body "<html> Dead job page </html>"})]
      (console/handler tu/redis-producer (mock/request :get (str "/dead/job/" (str (random-uuid)))))
      (is (true? (spy/called-once? dead/get-job))))))

(deftest enqueued-purge-queue-test
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

(deftest enqueued-delete-jobs-test
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

(deftest enqueued-prioritise-jobs-test
  (testing "Should prioritise 1 job given its encoded form in req params"
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
  (testing "Should prioritise more than 1 job given encoded job/s"
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

(deftest enqueued-get-job-test
  (testing "Should return a html view of enqueued job"
    (let [id (str (random-uuid))
          response (enqueued/get-job {:console-opts tu/redis-console-opts
                                      :params       {:id    id
                                                     :queue tu/queue}
                                      :prefix-route str
                                      :uri          (str "/goose/enqueued/queue/test/job/" id)})]
      (is (= 200 (:status response)))
      (is (str/starts-with? (:body response) "<!DOCTYPE html>"))))
  (testing "Should redirect to queue given invalid type of job-id"
    (let [response (enqueued/get-job {:console-opts tu/redis-console-opts
                                      :params       {:id    "not-uuid"
                                                     :queue tu/queue}
                                      :prefix-route str})]
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} response)))))

(deftest enqueued-prioritise-job-test
  (testing "Should prioritise a job given its encode form in req params"
    (f/create-jobs {:enqueued 2})
    (let [[_ j2] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 1)
          encoded-job (u/encode-to-str j2)]
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} (enqueued/prioritise-job {:console-opts tu/redis-console-opts
                                                      :params       {:queue tu/queue
                                                                     :job   encoded-job}
                                                      :prefix-route str})))
      (is (= [j2] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0))))))

(deftest enqueued-delete-job-test
  (testing "Should delete a job given its encoded form in req params"
    (f/create-jobs {:enqueued 3})
    (let [[first-job] (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)
          encoded-job (u/encode-to-str first-job)]
      (is (= 3 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= {:body    ""
              :headers {"Location" "/enqueued/queue/test"}
              :status  302} (enqueued/delete-job {:console-opts tu/redis-console-opts
                                                  :params       {:queue tu/queue
                                                                 :job   encoded-job}
                                                  :prefix-route str})))
      (is (= 2 (enqueued-jobs/size tu/redis-conn tu/queue))))))

(deftest dead-replay-jobs-test
  (testing "Should replay 1 dead job"
    (f/create-jobs {:dead 3})
    (let [[j2] (dead-jobs/get-by-range tu/redis-conn 1 1)
          encoded-job (u/encode-to-str j2)]
      (is (= 0 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= {:body    ""
              :headers {"Location" "/dead"}
              :status  302} (dead/replay-jobs {:console-opts tu/redis-console-opts
                                               :params       {:jobs encoded-job}
                                               :prefix-route str})))
      (is (= 1 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= (list j2) (enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)))
      (is (= 2 (dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should replay >1 dead jobs"
    (f/create-jobs {:dead 5})
    (let [jobs (dead-jobs/get-by-range tu/redis-conn 0 1)
          encoded-jobs (mapv u/encode-to-str jobs)]
      (is (= 0 (enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= {:body    ""
              :headers {"Location" "/dead"}
              :status  302} (dead/replay-jobs {:console-opts tu/redis-console-opts
                                               :params       {:jobs encoded-jobs}
                                               :prefix-route str}))))))

(deftest dead-delete-jobs-test
  (testing "Should delete 1 dead job"
    (f/create-jobs {:dead 2})
    (let [[j1] (dead-jobs/get-by-range tu/redis-conn 1 1)
          encoded-job (u/encode-to-str j1)]
      (is (= 2 (dead-jobs/size tu/redis-conn)))
      (is (= {:body    ""
              :headers {"Location" "/dead"}
              :status  302} (dead/delete-jobs {:console-opts tu/redis-console-opts
                                               :params       {:jobs encoded-job}
                                               :prefix-route str})))
      (is (= 1 (dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should delete >1 dead jobs"
    (f/create-jobs {:dead 5})
    (let [[j1 j2 j3 j4 j5] (dead-jobs/get-by-range tu/redis-conn 0 4)
          encoded-jobs (mapv u/encode-to-str [j2 j3 j1 j5])]
      (is (= 5 (dead-jobs/size tu/redis-conn)))
      (is (= {:body    ""
              :headers {"Location" "/dead"}
              :status  302} (dead/delete-jobs {:console-opts tu/redis-console-opts
                                               :params       {:jobs encoded-jobs}
                                               :prefix-route str})))
      (is (= 1 (dead-jobs/size tu/redis-conn)))
      (is (= [j4] (dead-jobs/get-by-range tu/redis-conn 0 4))))))

(deftest dead-purge-test
  (testing "Should purge dead queue"
    (f/create-jobs {:dead 12})
    (is (= 12 (dead-jobs/size tu/redis-conn)))
    (is (= {:body    ""
            :headers {"Location" "/dead"}
            :status  302} (dead/purge-queue {:console-opts tu/redis-console-opts
                                             :prefix-route str})))
    (is (= 0 (dead-jobs/size tu/redis-conn)))))

(deftest dead-get-job-test
  (testing "Should return html view of dead-job page"
    (f/create-jobs {:dead 1})
    (let [id (str (random-uuid))
          response (dead/get-job {:console-opts tu/redis-console-opts
                                  :params       {:id id}
                                  :prefix-route str})]
      (is (= 200 (:status response)))
      (is (str/starts-with? (:body response) "<!DOCTYPE html>"))))
  (testing "Should redirect to dead jobs page given invalid type of job-id"
    (let [response (dead/get-job {:console-opts tu/redis-console-opts
                                  :params       {:id "not-uuid"}
                                  :prefix-route str})]
      (is (= {:body    ""
              :headers {"Location" "/dead"}
              :status  302} response)))))
