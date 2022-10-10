(ns goose.redis.api-test
  (:require
    [goose.api.cron-jobs :as cron-entries]
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.api.scheduled-jobs :as scheduled-jobs]
    [goose.client :as c]
    [goose.retry :as retry]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]]))

; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

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
          _ (doseq [id (range 5)]
              (c/perform-async job-opts `dead-fn id)
              (Thread/sleep (rand-int 15))) ; Prevent jobs from dying at the same time
          circuit-breaker (atom 0)]
      ; Wait until 4 jobs have died after execution.
      (while (and (> 7 @circuit-breaker) (not= 7 @dead-fn-atom))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker)

      (is (= 7 (dead-jobs/size tu/redis-broker)))

      (is (= dead-job-id-1 (:id (dead-jobs/pop tu/redis-broker))))
      (let [dead-job (dead-jobs/find-by-id tu/redis-broker dead-job-id-2)]
        (is some? (dead-jobs/replay-job tu/redis-broker dead-job))
        (is true? (enqueued-jobs/delete tu/redis-broker dead-job)))

      (let [match? (fn [job] (= (list 0) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-broker match?)
            dead-at (get-in dead-job [:state :dead-at])]
        (is (true? (dead-jobs/delete-older-than tu/redis-broker dead-at))))

      (let [match? (fn [job] (= (list 1) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-broker match?)]
        (is (true? (dead-jobs/delete tu/redis-broker dead-job))))
      (is (= 2 (dead-jobs/replay-n-jobs tu/redis-broker 2)))
      (is (= 2 (enqueued-jobs/size tu/redis-broker (:queue job-opts))))

      (is (true? (dead-jobs/purge tu/redis-broker)))
      (is (= 0 (dead-jobs/replay-n-jobs tu/redis-broker 5))))))

(deftest cron-entries-test
  (testing "cron entries API"
    (c/perform-every tu/redis-client-opts
                     "my-cron-entry"
                     "* * * * *"
                     `tu/my-fn
                     :foo
                     "bar"
                     'baz)

    (is (= "my-cron-entry"
           (:name (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))))
    (is (= "* * * * *"
           (:cron-schedule (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))))
    (is (= {:execute-fn-sym `tu/my-fn
            :args           [:foo "bar" 'baz]}
           (-> (cron-entries/find-by-name tu/redis-broker "my-cron-entry")
               (:job-description)
               (select-keys [:execute-fn-sym :args]))))

    (is (cron-entries/delete tu/redis-broker "my-cron-entry")
        "delete returns truthy when an entry is deleted")
    (is (nil? (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))
        "The deleted entry should be absent")
    (is (not (cron-entries/delete tu/redis-broker "my-cron-entry"))
        "delete returns falsey when an entry is not deleted")

    (c/perform-every tu/redis-client-opts
                     "my-cron-entry"
                     "* * * * *"
                     `tu/my-fn
                     :foo
                     "bar"
                     'baz)
    (c/perform-every tu/redis-client-opts
                     "my-other-cron-entry"
                     "* * * * *"
                     `tu/my-fn
                     :foo
                     "bar"
                     'baz)

    (is (cron-entries/delete-all tu/redis-broker)
        "delete-all returns truthy if the cron entry keys were deleted")
    (is (nil? (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))
        "The deleted entry should be absent")
    (is (nil? (cron-entries/find-by-name tu/redis-broker "my-other-cron-entry"))
        "The deleted entry should be absent")

    (testing "adding an entry after delete-all was called"
      (c/perform-every tu/redis-client-opts
                       "my-cron-entry"
                       "* * * * *"
                       `tu/my-fn
                       :foo
                       "bar"
                       'baz)
      (is (= "my-cron-entry"
             (:name (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))))
      (is (= "* * * * *"
             (:cron-schedule (cron-entries/find-by-name tu/redis-broker "my-cron-entry"))))
      (is (= {:execute-fn-sym `tu/my-fn
              :args           [:foo "bar" 'baz]}
             (-> (cron-entries/find-by-name tu/redis-broker "my-cron-entry")
                 (:job-description)
                 (select-keys [:execute-fn-sym :args])))))))
