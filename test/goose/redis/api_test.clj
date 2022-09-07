(ns goose.redis.api-test
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
(use-fixtures :once tu/redis-fixture)

(deftest enqueued-jobs-test
  (testing "[redis] enqueued-jobs API"
    (let [job-id (:id (c/perform-async tu/redis-client-opts `tu/my-fn 1))
          _ (c/perform-async tu/redis-client-opts `tu/my-fn 2)]
      (is (= (list tu/queue) (enqueued-jobs/list-all-queues tu/redis-broker)))
      (is (= 2 (enqueued-jobs/size tu/redis-broker tu/queue)))
      (let [match? (fn [job] (= (list 2) (:args job)))]
        (is (= 1 (count (enqueued-jobs/find-by-pattern tu/redis-broker tu/queue match?)))))

      (let [job (enqueued-jobs/find-by-id tu/redis-broker tu/queue job-id)]
        (is (some? (enqueued-jobs/prioritise-execution tu/redis-broker job)))
        (is (true? (enqueued-jobs/delete tu/redis-broker job))))

      (is (true? (enqueued-jobs/purge tu/redis-broker tu/queue))))))

(deftest scheduled-jobs-test
  (testing "scheduled-jobs API"
    (let [job-id1 (:id (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 1))
          job-id2 (:id (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 2))
          _ (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 3)]
      (is (= 3 (scheduled-jobs/size tu/redis-broker)))
      (let [match? (fn [job] (not= (list 1) (:args job)))]
        (is (= 2 (count (scheduled-jobs/find-by-pattern tu/redis-broker match?)))))

      (let [job (scheduled-jobs/find-by-id tu/redis-broker job-id1)]
        (is (some? (scheduled-jobs/prioritise-execution tu/redis-broker job)))
        (is (false? (scheduled-jobs/delete tu/redis-broker job)))
        (is (true? (enqueued-jobs/delete tu/redis-broker job))))

      (let [job (scheduled-jobs/find-by-id tu/redis-broker job-id2)]
        (is (true? (scheduled-jobs/delete tu/redis-broker job))))

      (is (true? (scheduled-jobs/purge tu/redis-broker))))))

(defn death-handler [_ _ _])
(def dead-fn-atom (atom 0))
(defn dead-fn [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest dead-jobs-test
  (testing "[redis] dead-jobs API"
    (let [worker (w/start tu/redis-worker-opts)
          retry-opts (assoc retry/default-opts
                       :max-retries 0
                       :death-handler-fn-sym `death-handler)
          job-opts (assoc tu/redis-client-opts :retry-opts retry-opts)
          dead-job-id-1 (:id (c/perform-async job-opts `dead-fn 11))
          dead-job-id-2 (:id (c/perform-async job-opts `dead-fn 12))
          _ (doseq [id (range 3)] (c/perform-async job-opts `dead-fn id))
          circuit-breaker (atom 0)]
      ; Wait until 4 jobs have died after execution.
      (while (and (> 5 @circuit-breaker) (not= 5 @dead-fn-atom))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker)

      (is (= 5 (dead-jobs/size tu/redis-broker)))

      (is (= dead-job-id-1 (:id (dead-jobs/pop tu/redis-broker))))
      (let [dead-job (dead-jobs/find-by-id tu/redis-broker dead-job-id-2)]
        (is some? (dead-jobs/re-enqueue-for-execution tu/redis-broker dead-job))
        (is true? (enqueued-jobs/delete tu/redis-broker dead-job)))

      (let [match? (fn [job] (= (list 0) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-broker match?)
            dead-at (get-in dead-job [:state :dead-at])]
        (is (true? (dead-jobs/delete-older-than tu/redis-broker dead-at))))

      (let [match? (fn [job] (= (list 1) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-broker match?)]
        (is (true? (dead-jobs/delete tu/redis-broker dead-job))))

      (is (true? (dead-jobs/purge tu/redis-broker))))))
