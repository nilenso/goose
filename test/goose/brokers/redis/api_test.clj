(ns goose.brokers.redis.api-test
  (:require
    [goose.api.batch :as batch]
    [goose.api.cron-jobs :as cron-jobs]
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.api.enqueued-jobs :as redis-enqueued-jobs]
    [goose.brokers.redis.api.dead-jobs :as redis-dead-jobs]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.api.scheduled-jobs :as scheduled-jobs]
    [goose.batch]
    [goose.defaults :as d]
    [goose.client :as c]
    [goose.test-utils :as tu]
    [goose.factories :as f]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
    (java.time ZoneId)))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; This should be long enough for unit testing.
(def default-timeout-ms 1000)

(deftest enqueued-jobs-test
  (testing "[redis] enqueued-jobs API"
    (let [job-id (:id (c/perform-async tu/redis-client-opts `tu/my-fn 1))
          _ (c/perform-async tu/redis-client-opts `tu/my-fn 2)]
      (is (= (list tu/queue) (enqueued-jobs/list-all-queues tu/redis-producer)))
      (is (= 2 (enqueued-jobs/size tu/redis-producer tu/queue)))
      (let [match? (fn [job] (= (list 2) (:args job)))]
        (is (= 1 (count (enqueued-jobs/find-by-pattern tu/redis-producer tu/queue match?)))))

      (let [job (enqueued-jobs/find-by-id tu/redis-producer tu/queue job-id)]
        (is (= [["OK" "QUEUED" "QUEUED"] [1 2]] (enqueued-jobs/prioritise-execution tu/redis-producer job)))
        (is (enqueued-jobs/delete tu/redis-producer job)))

      (is (enqueued-jobs/purge tu/redis-producer tu/queue))))

  (testing "[redis] enqueued-jobs API over empty list"
    (let [queues (tu/with-timeout default-timeout-ms
                                  (enqueued-jobs/list-all-queues tu/redis-producer))]
      (is (and (not= :timed-out queues) (empty? queues))))
    (let [jobs (tu/with-timeout default-timeout-ms
                                (enqueued-jobs/find-by-pattern tu/redis-producer tu/queue (constantly true)))]
      (is (and (not= :timed-out jobs) (empty? jobs))))
    (let [job-id (str (random-uuid))]
      (is (nil? (tu/with-timeout default-timeout-ms
                                 (enqueued-jobs/find-by-id tu/redis-producer tu/queue job-id)))))))

(deftest enqueued-jobs-get-by-range-test
  (let [[id1 id2 id3] (doall (for [arg [1 2 3]]
                               (:id (c/perform-async tu/redis-client-opts `tu/my-fn arg))))
        [job1 job2 job3] (for [id [id1 id2 id3]]
                           (enqueued-jobs/find-by-id tu/redis-producer tu/queue id))]
    (testing "[redis] get jobs by range"
      (is (= [job1 job2] (redis-enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 1)))
      (is (= [job1] (redis-enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 0)))
      (is (= [job1 job2 job3] (redis-enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 2)))
      (is (= [job1 job2 job3] (redis-enqueued-jobs/get-by-range tu/redis-conn tu/queue 0 10))))))

(deftest enqueued-jobs-delete-multiple-test
  (let [[id1 id2 id3 id4] (doall (for [arg [1 2 3 4]]
                                   (:id (c/perform-async tu/redis-client-opts `tu/my-fn arg))))
        [job1 job2 job3 job4] (for [id [id1 id2 id3 id4]]
                                (enqueued-jobs/find-by-id tu/redis-producer tu/queue id))]
    (testing "[redis] delete multiple enqueued-job"
      (is (true? (redis-enqueued-jobs/delete tu/redis-conn tu/queue [job1 job2])))
      (is (= 2 (enqueued-jobs/size tu/redis-producer tu/queue))))

    (testing "[redis] delete single enqueued job from delete multiple jobs api"
      (is (true? (redis-enqueued-jobs/delete tu/redis-conn tu/queue [job3])))
      (is (= 1 (enqueued-jobs/size tu/redis-producer tu/queue))))

    (testing "[redis] delete only valid enqueued job"
      (is (false? (redis-enqueued-jobs/delete tu/redis-conn tu/queue [job4 {:job5 "invalid-job"}])))
      (is (= 0 (enqueued-jobs/size tu/redis-producer tu/queue))))))

(deftest enqueued-jobs-prioritise-multiple-test
  (let [[id1 id2 id3] (for [arg [1 2 3]]
                        (:id (c/perform-async tu/redis-client-opts `tu/my-fn arg)))
        [job1 job2 job3] (for [id [id1 id2 id3]]
                           (enqueued-jobs/find-by-id tu/redis-producer tu/queue id))]
    (testing "[redis] prioritise single job using prioritise-execution's multiple endpoint"
      (is (= [job1 job2 job3] (redis-cmds/range-from-front tu/redis-conn (d/prefix-queue tu/queue) 0 2)))
      (is (= [[["OK" "QUEUED" "QUEUED"] [1 3]]] (redis-enqueued-jobs/prioritise-execution tu/redis-conn tu/queue [job2])))
      (is (= [job2 job1 job3] (redis-cmds/range-from-front tu/redis-conn (d/prefix-queue tu/queue) 0 2))))

    (testing "[redis] prioritise multiple jobs"
      (is (= [[["OK" "QUEUED" "QUEUED"] [1 3]] [["OK" "QUEUED" "QUEUED"] [1 3]]]
             (redis-enqueued-jobs/prioritise-execution tu/redis-conn tu/queue [job1 job3])))
      (is (= [job3 job1 job2] (redis-cmds/range-from-front tu/redis-conn (d/prefix-queue tu/queue) 0 2))))

    (testing "[redis] prioritise only valid jobs"
      (is (= [[["OK" "QUEUED" "QUEUED"] [1 3]]] (redis-enqueued-jobs/prioritise-execution tu/redis-conn tu/queue [{:job4 "invalid"} job2 {:job5 "invalid"}])))
      (is (= [job2 job3 job1] (redis-cmds/range-from-front tu/redis-conn (d/prefix-queue tu/queue) 0 2))))))

(deftest scheduled-jobs-test
  (testing "[redis] scheduled-jobs API"
    (let [job-id1 (:id (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 1))
          job-id2 (:id (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 2))
          _ (c/perform-in-sec tu/redis-client-opts 10 `tu/my-fn 3)]
      (is (= 3 (scheduled-jobs/size tu/redis-producer)))
      (let [match? (fn [job] (not= (list 1) (:args job)))]
        (is (= 2 (count (scheduled-jobs/find-by-pattern tu/redis-producer match?)))))

      (let [job (scheduled-jobs/find-by-id tu/redis-producer job-id1)]
        (is (some? (scheduled-jobs/prioritise-execution tu/redis-producer job)))
        (is (false? (scheduled-jobs/delete tu/redis-producer job)))
        (is (enqueued-jobs/delete tu/redis-producer job)))

      (let [job (scheduled-jobs/find-by-id tu/redis-producer job-id2)]
        (is (scheduled-jobs/delete tu/redis-producer job)))

      (is (scheduled-jobs/purge tu/redis-producer))))

  (testing "[redis] scheduled-jobs API over empty list"
    (let [jobs (tu/with-timeout default-timeout-ms
                                (scheduled-jobs/find-by-pattern tu/redis-producer (constantly true)))]
      (is (and (not= :timed-out jobs) (empty? jobs))))
    (let [job-id (str (random-uuid))]
      (is (nil? (tu/with-timeout default-timeout-ms
                                 (scheduled-jobs/find-by-id tu/redis-producer job-id)))))))

(deftest dead-jobs-delete-test
  (testing "Should delete a single job and return true"
    (let [_ (f/create-jobs-in-redis {:dead 2})
          [d1 _] (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))]
      (is (= 2 (redis-dead-jobs/size tu/redis-conn)))
      (is (true? (redis-dead-jobs/delete tu/redis-conn d1)))
      (is (= 1 (redis-dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should delete all valid jobs and return true"
    (let [_ (f/create-jobs-in-redis {:dead 3})
          [d1 d2 d3] (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))]
      (is (= 3 (redis-dead-jobs/size tu/redis-conn)))
      (is (true? (redis-dead-jobs/delete tu/redis-conn d2 d3 d1)))
      (is (= 0 (redis-dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should ignore invalid jobs and return false"
    (let [_ (f/create-jobs-in-redis {:dead 3})
          [d1 _ d3] (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))]
      (is (= 3 (redis-dead-jobs/size tu/redis-conn)))
      (is (false? (redis-dead-jobs/delete tu/redis-conn d3 {:id "invalid-job"} d1)))
      (is (= 1 (redis-dead-jobs/size tu/redis-conn))))))

(deftest dead-jobs-replay-test
  (testing "Should return nil given invalid job"
    (let [_ (f/create-jobs-in-redis {:dead 2})]
      (is (= 2 (redis-dead-jobs/size tu/redis-conn)))
      (is (= [] (redis-dead-jobs/replay-jobs tu/redis-conn {:id "invalid-job"})))
      (is (= 2 (redis-dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should replay a single job and return response"
    (let [_ (f/create-jobs-in-redis {:dead 4})
          [d1 _] (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))]
      (is (= 4 (redis-dead-jobs/size tu/redis-conn)))
      (is (= [d1] (redis-dead-jobs/replay-jobs tu/redis-conn d1)))
      (is (= 1 (redis-enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= 3 (redis-dead-jobs/size tu/redis-conn)))))
  (tu/clear-redis)
  (testing "Should replay multiple jobs and return count of jobs that are replayed"
    (let [_ (f/create-jobs-in-redis {:dead 4})
          [d1 d2 _ d4] (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))]
      (is (= 4 (redis-dead-jobs/size tu/redis-conn)))
      (is (= [d1 d4 d2] (redis-dead-jobs/replay-jobs tu/redis-conn d1 {:id "invalid-job1"} d4 {:id "invalid-job2"} d2)))
      (is (= 3 (redis-enqueued-jobs/size tu/redis-conn tu/queue)))
      (is (= 1 (redis-dead-jobs/size tu/redis-conn))))))

(deftest dead-job-get-by-range-test
  (testing "Should get jobs in decreasing order of died-at given a range"
    (let [_ (f/create-jobs-in-redis {:dead 3})
          [j1 j2] (redis-dead-jobs/get-by-range tu/redis-conn 0 1)]
      (is (true? (>= (get-in j1 [:state :died-at]) (get-in j2 [:state :died-at]))))))
  (tu/clear-redis)
  (testing "Should get max of (size dead-jobs) given high stop value in range"
    (let [_ (f/create-jobs-in-redis {:dead 2})
          jobs-from-match (dead-jobs/find-by-pattern tu/redis-producer (fn [_] true))
          jobs-from-range (redis-dead-jobs/get-by-range tu/redis-conn 0 100)]
      (is (= (set jobs-from-match) (set jobs-from-range)))
      (is (= 2 (count jobs-from-range))))))

(def dead-fn-atom (atom 0))
(defn dead-fn
  [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest dead-jobs-test
  (testing "[redis] dead-jobs API"
    (let [worker (w/start tu/redis-worker-opts)
          retry-opts (assoc tu/retry-opts :max-retries 0)
          job-opts (assoc tu/redis-client-opts :retry-opts retry-opts)
          dead-job-id-1 (:id (c/perform-async job-opts `dead-fn 11))
          _ (Thread/sleep ^long (rand-int 15))
          dead-job-id-2 (:id (c/perform-async job-opts `dead-fn 12))
          _ (doseq [id (range 5)]
              (c/perform-async job-opts `dead-fn id)
              (Thread/sleep ^long (rand-int 15))) ; Prevent jobs from dying at the same time
          circuit-breaker (atom 0)]
      ; Wait until 4 jobs have died after execution.
      (while (and (> 7 @circuit-breaker) (not= 7 @dead-fn-atom))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker)

      (is (= 7 (dead-jobs/size tu/redis-producer)))

      (is (= dead-job-id-1 (:id (dead-jobs/pop tu/redis-producer))))
      (let [dead-job (dead-jobs/find-by-id tu/redis-producer dead-job-id-2)]
        (is some? (dead-jobs/replay-job tu/redis-producer dead-job))
        (enqueued-jobs/delete tu/redis-producer dead-job))

      (let [match? (fn [job] (= (list 0) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-producer match?)
            died-at (get-in dead-job [:state :died-at])]
        (is (dead-jobs/delete-older-than tu/redis-producer died-at)))

      (let [match? (fn [job] (= (list 1) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern tu/redis-producer match?)]
        (is (dead-jobs/delete tu/redis-producer dead-job)))
      (is (= 2 (dead-jobs/replay-n-jobs tu/redis-producer 2)))
      (is (= 2 (enqueued-jobs/size tu/redis-producer (:queue job-opts))))

      (is (dead-jobs/purge tu/redis-producer))
      (is (= 0 (dead-jobs/replay-n-jobs tu/redis-producer 5)))))

  (testing "[redis] dead-jobs API over empty list"
    (let [jobs (tu/with-timeout default-timeout-ms
                                (dead-jobs/find-by-pattern tu/redis-producer (constantly true)))]
      (is (and (not= :timed-out jobs) (empty? jobs))))
    (let [job-id (str (random-uuid))]
      (is (nil? (tu/with-timeout default-timeout-ms
                                 (dead-jobs/find-by-id tu/redis-producer job-id)))))))

(deftest cron-entries-test
  (testing "cron entries API"
    (let [recurring-job (c/perform-every tu/redis-client-opts
                                         {:cron-name     "my-cron-entry"
                                          :cron-schedule "* * * * *"
                                          :timezone      "US/Pacific"}
                                         `tu/my-fn
                                         :foo
                                         "bar"
                                         'baz)]
      (is (= "my-cron-entry" (:cron-name recurring-job)))
      (is (= "* * * * *" (:cron-schedule recurring-job)))
      (is (= "US/Pacific" (:timezone recurring-job))))


    (is (= "my-cron-entry"
           (:cron-name (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
    (is (= "* * * * *"
           (:cron-schedule (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
    (is (= "US/Pacific"
           (:timezone (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
    (is (= {:execute-fn-sym `tu/my-fn
            :args           [:foo "bar" 'baz]}
           (-> (cron-jobs/find-by-name tu/redis-producer "my-cron-entry")
               (:job-description)
               (select-keys [:execute-fn-sym :args]))))

    (is (cron-jobs/delete tu/redis-producer "my-cron-entry")
        "delete returns truthy when an entry is deleted")
    (is (nil? (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))
        "The deleted entry should be absent")
    (is (not (cron-jobs/delete tu/redis-producer "my-cron-entry"))
        "delete returns falsey when an entry is not deleted")

    (c/perform-every tu/redis-client-opts
                     {:cron-name     "my-cron-entry"
                      :cron-schedule "* * * * *"}
                     `tu/my-fn
                     :foo
                     "bar"
                     'baz)
    (c/perform-every tu/redis-client-opts
                     {:cron-name     "my-other-cron-entry"
                      :cron-schedule "* * * * *"}
                     `tu/my-fn
                     :foo
                     "bar"
                     'baz)

    (is (= 2 (cron-jobs/size tu/redis-producer)))
    (is (cron-jobs/purge tu/redis-producer)
        "delete-all returns truthy if the cron entry keys were deleted")
    (is (= 0 (cron-jobs/size tu/redis-producer)))

    (testing "adding an entry after delete-all was called"
      (c/perform-every tu/redis-client-opts
                       {:cron-name     "my-cron-entry"
                        :cron-schedule "* * * * *"}
                       `tu/my-fn
                       :foo
                       "bar"
                       'baz)
      (is (= "my-cron-entry"
             (:cron-name (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
      (is (= "* * * * *"
             (:cron-schedule (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
      (is (= (.getId (ZoneId/systemDefault))
             (:timezone (cron-jobs/find-by-name tu/redis-producer "my-cron-entry"))))
      (is (= {:execute-fn-sym `tu/my-fn
              :args           [:foo "bar" 'baz]}
             (-> (cron-jobs/find-by-name tu/redis-producer "my-cron-entry")
                 (:job-description)
                 (select-keys [:execute-fn-sym :args])))))))

(def job-failed-atom (atom (promise)))
(defn failing-fn [arg]
  (deliver @job-failed-atom arg)
  (/ 1 0))
(def callback-fn-atom (atom (promise)))
(defn callback-fn [batch-id _]
  (deliver @callback-fn-atom batch-id))

(deftest batch-test
  (let [arg "foo"
        batch-opts {:linger-sec      1
                    :callback-fn-sym `callback-fn}
        batch-args (map list [arg])
        batch-job? (fn [job] (:batch-id job))]
    (testing "[redis] batch API"
      (let [batch-id (:id (goose.client/perform-batch tu/redis-client-opts batch-opts `tu/my-fn batch-args))
            expected-batch {:id       batch-id
                            :status   goose.batch/status-in-progress
                            :total    1
                            :enqueued 1
                            :retrying 0
                            :success  0
                            :dead     0}
            {:keys [created-at] :as batch} (batch/status tu/redis-producer batch-id)]
        (is (= expected-batch (dissoc batch :created-at)))
        (is (= java.lang.Long (type created-at)))
        (is (= 2 (batch/delete tu/redis-producer batch-id)))))

    (testing "[redis] batch API with invalid id"
      (is (nil? (batch/status tu/redis-producer "some-id")))
      (is (nil? (batch/delete tu/redis-producer "some-id"))))

    (testing "[redis] batch API for a cleaned-up/expired batch"
      (reset! callback-fn-atom (promise))
      (let [batch-opts (assoc batch-opts :linger-sec 0)
            batch-id (:id (goose.client/perform-batch tu/redis-client-opts batch-opts `tu/my-fn batch-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-atom 200 :api-test-timed-out) batch-id))
        (is (nil? (batch/status tu/redis-producer batch-id)))
        (w/stop worker)))

    (testing "[redis] `batch/delete` for enqueued jobs"
      (let [batch-id (:id (goose.client/perform-batch tu/redis-client-opts batch-opts `tu/my-fn batch-args))
            queue (:queue tu/redis-client-opts)]

        (is (not-empty (enqueued-jobs/find-by-pattern tu/redis-producer queue batch-job?)))
        (batch/delete tu/redis-producer batch-id)
        (is (empty? (enqueued-jobs/find-by-pattern tu/redis-producer queue batch-job?)))))

    (testing "[redis] `batch/delete` for scheduled retrying jobs"
      (reset! job-failed-atom (promise))
      (let [batch-id (:id (goose.client/perform-batch tu/redis-client-opts batch-opts `failing-fn batch-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @job-failed-atom 100 :batch-delete-scheduled-retry-test-timed-out) arg))
        (w/stop worker)

        (is (not-empty (scheduled-jobs/find-by-pattern tu/redis-producer batch-job?)))
        (batch/delete tu/redis-producer batch-id)
        (is (empty? (scheduled-jobs/find-by-pattern tu/redis-producer batch-job?)))))

    (testing "[redis] `batch/delete` for enqueued retrying jobs"
      (reset! job-failed-atom (promise))
      (let [retry-queue "batch-delete-api-test"
            client-opts (assoc-in tu/redis-client-opts [:retry-opts :retry-queue] retry-queue)
            batch-id (:id (goose.client/perform-batch client-opts batch-opts `failing-fn batch-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @job-failed-atom 100 :batch-delete-enqueued-retry-test-timed-out) arg))
        (w/stop worker)
        (let [retrying-job (scheduled-jobs/find-by-pattern tu/redis-producer batch-job?)]
          ;; Move a retrying job from scheduled to ready-queue, and then call `batch/delete`.
          (scheduled-jobs/prioritise-execution tu/redis-producer (first retrying-job)))

        (is (not-empty (enqueued-jobs/find-by-pattern tu/redis-producer retry-queue batch-job?)))
        (batch/delete tu/redis-producer batch-id)
        (is (empty? (enqueued-jobs/find-by-pattern tu/redis-producer retry-queue batch-job?)))))))
