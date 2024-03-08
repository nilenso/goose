(ns goose.brokers.redis.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.console :as redis-console]
            [goose.factories :as f]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock])
  (:import (java.io File)))

(use-fixtures :each tu/redis-fixture)

(deftest jobs-size-test
  (testing "Should return job size for enqueued, scheduled, periodic and dead jobs"
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 0 :scheduled 0
                                                    :periodic 0 :dead 0}))
    (f/add-jobs {:enqueued 2 :scheduled 3 :periodic 2 :dead 3})
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 2 :scheduled 3
                                                    :periodic 2 :dead 3})))
  (testing "Should return jobs size given jobs exist in multiple queues"
    (f/add-jobs {:enqueued 3 :scheduled 3 :periodic 1 :dead 1}
                {:enqueued {:queue       "queue1"
                            :ready-queue "goose/queue:queue1"}})
    (is (= (redis-console/jobs-size tu/redis-conn) {:enqueued 5 :scheduled 6 :periodic 3 :dead 4}))))

(deftest handler-test
  (testing "Should serve css file on GET request at /css/style.css route"
    (let [response (redis-console/handler tu/redis-producer (-> (mock/request :get "/goose/console/css/style.css")
                                                                (assoc :client-opts {:broker       tu/redis-producer
                                                                                     :app-name     ""
                                                                                     :route-prefix "goose/console/"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "text/css"))))
  (testing "Should serve goose logo on GET request at img/goose-logo.png route"
    (let [response (redis-console/handler tu/redis-producer (-> (mock/request :get "foo/img/goose-logo.png")
                                                                (assoc :client-opts {:broker       tu/redis-producer
                                                                                     :app-name     ""
                                                                                     :route-prefix "foo"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "image/png"))))
  (testing "Should redirect to main route with slash (goose/console/) on GET req to route without slash(goose/console)"
    (is (= (redis-console/handler tu/redis-producer (-> (mock/request :get "foo")
                                                        (assoc :client-opts {:broker       tu/redis-producer
                                                                             :app-name     ""
                                                                             :route-prefix "foo"})))
           {:status  302
            :headers {"Location" "foo/"}
            :body    ""})))
  (testing "Should show not found page given invalid route"
    (is (= (redis-console/handler tu/redis-producer (-> (mock/request :get "foo/invalid")
                                                        (assoc :client-opts {:broker       tu/redis-producer
                                                                             :app-name     ""
                                                                             :route-prefix "foo"})))
           {:body    "<div> Not found </div>"
            :headers {}
            :status  404}))))
