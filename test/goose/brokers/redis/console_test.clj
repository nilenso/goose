(ns goose.brokers.redis.console-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.console :as console]
            [goose.test-utils :as tu]
            [ring.mock.request :as mock])
  (:import (java.io File)))

(use-fixtures :each tu/redis-fixture)

(deftest handler-test
  (testing "Should serve css file on GET request at /css/style.css route"
    (let [response (console/handler tu/redis-producer (-> (mock/request :get "goose/console/css/style.css")
                                                          (assoc :console-opts {:broker       tu/redis-producer
                                                                                :app-name     ""
                                                                                :route-prefix "goose/console"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "text/css"))))
  (testing "Should serve goose logo on GET request at /img/goose-logo.png route"
    (let [response (console/handler tu/redis-producer (-> (mock/request :get "foo/img/goose-logo.png")
                                                          (assoc :console-opts {:broker       tu/redis-producer
                                                                                :app-name     ""
                                                                                :route-prefix "foo"})))]
      (is (= (:status response) 200))
      (is (= (type (:body response)) File))
      (is (= (get-in response [:headers "Content-Type"]) "image/png"))))
  (testing "Should redirect to main route with slash (goose/console/) on GET req to route without slash(goose/console)"
    (is (= (console/handler tu/redis-producer (-> (mock/request :get "foo")
                                                  (assoc :console-opts {:broker       tu/redis-producer
                                                                        :app-name     ""
                                                                        :route-prefix "foo"}
                                                         :prefix-route (partial str "foo"))))
           {:status  302
            :headers {"Location" "foo/"}
            :body    ""})))
  (testing "Should show not-found page given invalid route"
    (is (= (console/handler tu/redis-producer (-> (mock/request :get "foo/invalid")
                                                  (assoc :console-opts {:broker       tu/redis-producer
                                                                        :app-name     ""
                                                                        :route-prefix "foo"})))
           {:body    "<div> Not found </div>"
            :headers {}
            :status  404}))))
