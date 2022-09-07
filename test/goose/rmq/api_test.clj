(ns goose.rmq.api-test
  (:require
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.client :as c]
    [goose.test-utils :as tu]

    [clojure.test :refer [deftest is testing use-fixtures]]))

; ======= Setup & Teardown ==========
(use-fixtures :once tu/rmq-fixture)

(deftest enqueued-jobs-test
  (testing "[rmq] enqueued-jobs API"
    (c/perform-async tu/rmq-client-opts `tu/my-fn)
    (is (= 1 (enqueued-jobs/size tu/client-rmq-broker tu/queue)))
    (enqueued-jobs/purge tu/client-rmq-broker tu/queue)
    (is (= 0 (enqueued-jobs/size tu/client-rmq-broker tu/queue)))))
