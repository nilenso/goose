(ns goose.api-test
  (:require
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.api.scheduled-jobs :as scheduled-jobs]
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]]))

; ======= Setup & Teardown ==========
(use-fixtures :once tu/fixture)

(deftest enqueued-jobs-test
  (testing "enqueued-jobs API"
    (let [job-id (c/perform-async tu/client-opts `tu/my-fn 1)
          _ (c/perform-async tu/client-opts `tu/my-fn 2)]
      (is (= (list tu/queue) (enqueued-jobs/list-all-queues tu/broker-opts)))
      (is (= 2 (enqueued-jobs/size tu/broker-opts tu/queue)))
      (let [match? (fn [job] (= (list 2) (:args job)))]
        (is (= 1 (count (enqueued-jobs/find-by-pattern tu/broker-opts tu/queue match?)))))

      (let [job (enqueued-jobs/find-by-id tu/broker-opts tu/queue job-id)]
        (is (some? (enqueued-jobs/prioritise-execution tu/broker-opts tu/queue job)))
        (is (true? (enqueued-jobs/delete tu/broker-opts tu/queue job))))

      (is (true? (enqueued-jobs/delete-all tu/broker-opts tu/queue))))))

(deftest scheduled-jobs-test
  (testing "scheduled-jobs API"
    (let [job-id1 (c/perform-in-sec tu/client-opts 10 `tu/my-fn 1)
          job-id2 (c/perform-in-sec tu/client-opts 10 `tu/my-fn 2)
          _ (c/perform-in-sec tu/client-opts 10 `tu/my-fn 3)]
      (is (= 3 (scheduled-jobs/size tu/broker-opts)))
      (let [match? (fn [job] (not= (list 1) (:args job)))]
        (is (= 2 (count (scheduled-jobs/find-by-pattern tu/broker-opts match?)))))

      (let [job (scheduled-jobs/find-by-id tu/broker-opts job-id1)]
        (is (some? (scheduled-jobs/prioritise-execution tu/broker-opts job)))
        (is (false? (scheduled-jobs/delete tu/broker-opts job)))
        (is (true? (enqueued-jobs/delete tu/broker-opts tu/queue job))))

      (let [job (scheduled-jobs/find-by-id tu/broker-opts job-id2)]
        (is (true? (scheduled-jobs/delete tu/broker-opts job))))

      (is (true? (scheduled-jobs/delete-all tu/broker-opts))))))

(defn death-handler [_ _ _])
(def dead-fn-atom (atom 0))
(defn dead-fn [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest dead-jobs-test
  (testing "dead-jobs API"
    (let [worker (w/start tu/worker-opts)
          retry-opts (assoc retry/default-opts
                       :max-retries 0
                       :death-handler-fn-sym `death-handler)
          job-opts (assoc tu/client-opts :retry-opts retry-opts)
          dead-job-id (c/perform-async job-opts `dead-fn -1)
          _ (doseq [id (range 3)] (c/perform-async job-opts `dead-fn id))
          circuit-breaker (atom 0)]
      ; Wait until 4 jobs have died after execution.
      (while (and (> 4 @circuit-breaker) (not= 4 @dead-fn-atom))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker)

      (is (= 4 (dead-jobs/size tu/broker-opts)))

      (let [dead-job (dead-jobs/find-by-id tu/broker-opts dead-job-id)]
        (is some? (dead-jobs/re-enqueue-for-execution tu/broker-opts dead-job))
        (is true? (enqueued-jobs/delete tu/broker-opts tu/queue dead-job)))

      (let [match? (fn [job] (= (list 0) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/broker-opts match?)
            dead-at (get-in dead-job [:state :dead-at])]
        (is (true? (dead-jobs/delete-older-than tu/broker-opts dead-at))))

      (let [match? (fn [job] (= (list 1) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/broker-opts match?)]
        (is (true? (dead-jobs/delete tu/broker-opts dead-job))))

      (is (true? (dead-jobs/delete-all tu/broker-opts))))))
