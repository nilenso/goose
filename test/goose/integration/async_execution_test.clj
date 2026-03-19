(ns goose.integration.async-execution-test
  (:require
   [goose.client :as c]
   [goose.worker :as w]
   [goose.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each tu/redis-fixture)

(deftest perform-async-test
  (testing "Goose executes a function asynchronously"
    (let [res (promise)
          deliver-fn (let [test-ns 'goose.integration.async-execution-test.tmp.perform-async-test
                           fn-sym 'deliver-result
                           test-fn #(deliver res %)]
                       (create-ns test-ns)
                       (intern test-ns fn-sym test-fn)
                       (symbol (str test-ns) (str fn-sym)))]
      (c/perform-async tu/redis-client-opts deliver-fn "async-execute-test")
      (let [worker (w/start tu/redis-worker-opts)]
        (is (= (deref res 1000 :timed-out) "async-execute-test"))
        (w/stop worker)))))
