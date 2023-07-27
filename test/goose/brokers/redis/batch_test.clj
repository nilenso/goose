(ns goose.brokers.redis.batch-test
  (:require
    [goose.brokers.redis.batch :as redis-batch]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.test-utils :as tu]

    [clojure.test :refer [deftest is testing use-fixtures]]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ======= TEST: Batch state creation ==========
(def batch-id (str (random-uuid)))

(def batch
  {:id              batch-id
   :callback-fn-sym `prn
   :jobs            '({:id             (str (random-uuid))
                       :execute-fn-sym clojure.core/prn
                       :args           ["foo"]
                       :queue          tu/queue
                       :ready-queue    (d/prefix-queue tu/queue)
                       :retry-opts     retry/default-opts
                       :enqueued-at    (u/epoch-time-ms)
                       :batch-id       batch-id}
                      {:id             (str (random-uuid))
                       :execute-fn-sym clojure.core/prn
                       :args           ["bar"]
                       :queue          tu/queue
                       :ready-queue    (d/prefix-queue tu/queue)
                       :retry-opts     retry/default-opts
                       :enqueued-at    (u/epoch-time-ms)
                       :batch-id       batch-id})})

(deftest enqueue-test
  (testing "Broker creates batch state and enqueues jobs"
    (let [batch-id (:id batch)
          batch-state (select-keys batch [:id :callback-fn-sym])
          batch-state-key (d/prefix-batch batch-id)
          enqueued-job-set (d/construct-batch-job-set batch-id d/enqueued-job-set)
          job-ids (->>
                    (map :id (:jobs batch))
                    (set))]
      (is (= (redis-cmds/exists tu/redis-conn batch-state-key) 0))
      (is (= (redis-cmds/exists tu/redis-conn enqueued-job-set) 0))

      (redis-batch/enqueue tu/redis-conn batch)

      (is (= (redis-cmds/parse-map tu/redis-conn batch-state-key) batch-state))
      (is (= (redis-cmds/set-members tu/redis-conn enqueued-job-set) job-ids)))))