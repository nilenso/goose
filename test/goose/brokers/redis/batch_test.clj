(ns goose.brokers.redis.batch-test
  (:require
    [goose.brokers.redis.batch :as redis-batch]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.test-utils :as tu]

    [clojure.test :refer [deftest is testing use-fixtures]]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ====================================

(def opts {:redis-conn tu/redis-conn})
(def batch-id (str (random-uuid)))
(def job-id (str (random-uuid)))
(def job {:id         job-id
          :batch-id   batch-id
          :retry-opts {:max-retries 1}})
(def enqueued-job-set (d/construct-batch-job-set batch-id d/enqueued-job-set))
(def successful-job-set (d/construct-batch-job-set batch-id d/successful-job-set))
(def dead-job-set (d/construct-batch-job-set batch-id d/dead-job-set))
(def retry-job-set (d/construct-batch-job-set batch-id d/retrying-job-set))

;;; ======= TEST: Batch state creation ==========

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
          job-ids (->>
                    (map :id (:jobs batch))
                    (set))]
      (is (= (redis-cmds/exists tu/redis-conn batch-state-key) 0))
      (is (= (redis-cmds/exists tu/redis-conn enqueued-job-set) 0))

      (redis-batch/enqueue tu/redis-conn batch)

      (is (= (redis-cmds/parse-map tu/redis-conn batch-state-key) batch-state))
      (is (= (redis-cmds/set-members tu/redis-conn enqueued-job-set) job-ids)))))

;;;; ======= TEST: Batch state update middleware ===========

(deftest enqueued-to-successful
  (testing "Job state is transitioned from enqueued -> successful on successful job execution"
    (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
    ((redis-batch/wrap-state-update (fn [_opts _job] "Function executed"))
     opts job)
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  enqueued-job-set)))
    (is (= 1 (redis-cmds/set-size (:redis-conn opts)
                                  successful-job-set)))
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  retry-job-set)))
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  dead-job-set)))))

(deftest enqueued-to-retrying
  (testing "Job state is transitioned from enqueued -> retrying on job failure"
    (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
    (is (thrown-with-msg? Exception #"Exception"
                          ((redis-batch/wrap-state-update (fn [_opts _job]
                                                            (throw (Exception. "Exception"))))
                           opts job)))
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  enqueued-job-set)))
    (is (= 1 (redis-cmds/set-size (:redis-conn opts)
                                  retry-job-set)))
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  successful-job-set)))
    (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                  dead-job-set)))))

(deftest enqueued-to-dead
  (testing "Job state transitioned from enqueued -> dead on job failure without retries"
    (let [job (assoc job :retry-opts {:max-retries 0})]
      (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
      (is (thrown-with-msg? Exception #"Exception"
                            ((redis-batch/wrap-state-update (fn [_opts _job]
                                                              (throw (Exception. "Exception"))))
                             opts job)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    enqueued-job-set)))
      (is (= 1 (redis-cmds/set-size (:redis-conn opts)
                                    dead-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    retry-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    successful-job-set))))))

(deftest retrying-to-successful
  (testing "Job state transitioned from retrying -> successful on successful execution of retrying job"
    (let [job (assoc job :state {:error       "Exception"
                                 :retry-count 0})]
      (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)
      ((redis-batch/wrap-state-update (fn [_opts _job] "Function Executed")) opts job)
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    retry-job-set)))
      (is (= 1 (redis-cmds/set-size (:redis-conn opts)
                                    successful-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    enqueued-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    dead-job-set))))))

(deftest retrying-to-retrying
  (testing "Job state remains in retrying on failure of retrying job"
    (let [job (assoc job :state {:error       "Exception"
                                 :retry-count 0}
                         :retry-opts {:max-retries 2})
          _ (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)]
      (is (thrown-with-msg? Exception #"Exception"
                            ((redis-batch/wrap-state-update (fn [_opts _job]
                                                              (throw (Exception. "Exception"))))
                             opts job)))

      (is (= 1 (redis-cmds/set-size (:redis-conn opts) retry-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    enqueued-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    successful-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    dead-job-set))))))

(deftest retrying-to-dead
  (testing "Job state transitioned from retrying -> dead on exhausting job retries"
    (let [job (assoc job :state {:error       "Exception"
                                 :retry-count 0})
          _ (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)]
      (is (thrown-with-msg? Exception #"Exception"
                            ((redis-batch/wrap-state-update (fn [_opts _job]
                                                              (throw (Exception. "Exception"))))
                             opts job)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    retry-job-set)))
      (is (= 1 (redis-cmds/set-size (:redis-conn opts)
                                    dead-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    enqueued-job-set)))
      (is (= 0 (redis-cmds/set-size (:redis-conn opts)
                                    successful-job-set))))))
