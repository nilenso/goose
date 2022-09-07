(ns goose.rmq.api-test
  (:require
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]]))

; ======= Setup & Teardown ==========
(use-fixtures :once tu/rmq-fixture)

(deftest enqueued-jobs-test
  (testing "[rmq] enqueued-jobs API"
    (c/perform-async tu/rmq-client-opts `tu/my-fn)
    (is (= 1 (enqueued-jobs/size tu/client-rmq-broker tu/queue)))
    (enqueued-jobs/purge tu/client-rmq-broker tu/queue)
    (is (= 0 (enqueued-jobs/size tu/client-rmq-broker tu/queue)))))

(def job-dead (promise))
(defn dead-fn []
  (throw (Exception. "died!")))
(defn dead-test-death-handler [_ _ ex]
  (deliver job-dead ex))

(deftest dead-jobs-test
  (testing "[rmq] dead-jobs API"
    (let [retry-opts (assoc retry/default-opts
                       :max-retries 0
                       :death-handler-fn-sym `dead-test-death-handler)
          job-opts (assoc tu/rmq-client-opts :retry-opts retry-opts)
          _ (c/perform-async job-opts `dead-fn)
          worker (w/start tu/rmq-worker-opts)]
      (is (= java.lang.Exception (type (deref job-dead 100 :dead-jobs-api-test-timed-out))))
      (w/stop worker)
      (is (= 1 (dead-jobs/size tu/client-rmq-broker)))
      (dead-jobs/purge tu/client-rmq-broker)
      (is (= 0 (dead-jobs/size tu/client-rmq-broker))))))
