(ns goose.brokers.rmq.console-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [goose.brokers.rmq.api.dead-jobs :as dead-jobs]
    [goose.brokers.rmq.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.rmq.console :as console]
    [goose.factories :as f]
    [goose.test-utils :as tu]
    [goose.utils :as u]
    [ring.mock.request :as mock]
    [spy.core :as spy]))

(use-fixtures :each tu/rmq-fixture)

(deftest get-homepage-test
  (testing "Should show rmq's home page even if no jobs in dead queue"
    (let [response (console/homepage {:console-opts tu/rmq-console-opts
                                      :prefix-route str})]
      (is (= 200 (:status response)))
      (is (str/starts-with? (:body response) "<!DOCTYPE html>")))))

(deftest page-handler-test
  (testing "Handler should invoke homepage handler"
    (with-redefs [console/homepage (spy/stub {:status 200 :body "Mocked resp"})]
      (console/handler tu/rmq-producer (mock/request :get "/"))
      (is (true? (spy/called-once? console/homepage)))
      (is (= [{:status 200 :body "Mocked resp"}] (spy/responses console/homepage)))))

  (testing "Handler should invoke get-dead-job page handler"
    (with-redefs [console/get-dead-job (spy/stub {:status 200 :body "<html> Dead jobs </html>"})]
      (console/handler tu/rmq-producer (mock/request :get "/dead"))
      (is (true? (spy/called-once? console/get-dead-job)))
      (is (= [{:status 200 :body "<html> Dead jobs </html>"}] (spy/responses console/get-dead-job)))))

  (testing "Handler should invoke purge-dead-queue handler"
    (with-redefs [console/purge-dead-queue (spy/stub {:status 302 "Location" "/dead" :body ""})]
      (console/handler tu/rmq-producer (mock/request :delete "/dead"))
      (is (true? (spy/called-once? console/purge-dead-queue)))
      (is (= [{:status 302 "Location" "/dead" :body ""}] (spy/responses console/purge-dead-queue)))))

  (testing "Handler should invoke pop-dead-queue handler"
    (with-redefs [console/pop-dead-queue (spy/stub {:status 200 :body "<html> Some jobs popped </html>"})]
      (console/handler tu/rmq-producer (mock/request :delete "/dead/job"))
      (is (true? (spy/called-once? console/pop-dead-queue)))
      (is (= [{:status 200 :body "<html> Some jobs popped </html>"}] (spy/responses console/pop-dead-queue)))))

  (testing "Handler should invoke replay-jobs queue handler"
    (with-redefs [console/replay-jobs (spy/stub {:status 200 :body "<html> Few jobs replayed </html>"})]
      (console/handler tu/rmq-producer (mock/request :post "/dead/jobs"))
      (is (true? (spy/called-once? console/replay-jobs)))
      (is (= [{:status 200 :body "<html> Few jobs replayed </html>"}] (spy/responses console/replay-jobs))))))

(deftest purge-queue-test
  (testing "Should delete all jobs present in queue"
    (f/create-jobs-in-rmq {:dead 3})
    (is (= 3 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))
    (is (= {:status  302
            :headers {"Location" "/dead"}
            :body    ""} (console/purge-dead-queue {:console-opts tu/rmq-console-opts
                                                    :prefix-route str})))
    (is (= 0 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))))

(deftest replay-jobs-test
  (testing "Should replay n jobs"
    (f/create-jobs-in-rmq {:dead 4})
    (is (= 4 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))
    (is (= {:status  200
            :headers {}} (select-keys (console/replay-jobs {:console-opts tu/rmq-console-opts
                                                            :prefix-route str
                                                            :params       {:replay "3"}}) [:status :headers])))
    (is (= 1 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))
    (is (= 3 (enqueued-jobs/size (u/random-element (:channels tu/rmq-producer)) tu/queue)))))

(deftest pop-dead-queue
  (testing "Should pop dead job"
    (f/create-jobs-in-rmq {:dead 2})
    (is (= 2 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))
    (is (= {:status  200
            :headers {}} (select-keys (console/pop-dead-queue {:console-opts tu/rmq-console-opts
                                                               :prefix-route str}) [:status :headers])))
    (is (= 1 (dead-jobs/size (u/random-element (:channels tu/rmq-producer)))))))

(deftest get-dead-job-test
  (testing "Should return dead job's page"
    (f/create-jobs-in-rmq {:dead 2})
    (let [response (console/get-dead-job {:console-opts tu/rmq-console-opts
                                          :prefix-route str})]
      (is (= {:status  200
              :headers {}} (select-keys response [:status :headers])))
      (is (str/starts-with? (:body response) "<!DOCTYPE ")))))
