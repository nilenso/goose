(ns goose.brokers.redis.batch-test
  (:require
    [goose.brokers.redis.batch :as redis-batch]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.retry :as retry]
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
   :linger-sec      3600
   :queue           tu/queue
   :ready-queue     (d/prefix-queue tu/queue)
   :retry-opts      retry/default-opts
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
          batch-state (->> (select-keys batch redis-batch/batch-state-keys)
                           ;; Redis returns all values as strings
                           ;; This is needed so that they can be compared with values returned from Redis
                           (reduce (fn [m [k v]] (assoc m k (str v))) {}))
          batch-state-key (d/prefix-batch batch-id)
          job-ids (->>
                    (map :id (:jobs batch))
                    (set))]
      (is (= (redis-cmds/exists tu/redis-conn batch-state-key) 0))
      (is (= (redis-cmds/exists tu/redis-conn enqueued-job-set) 0))

      (redis-batch/enqueue tu/redis-conn batch)

      (is (= (->> (redis-cmds/parse-map tu/redis-conn batch-state-key)
                  ;; This is needed so that de-serialized values are strings and can be compared with batch-state
                  (reduce (fn [m [k v]] (assoc m k (str v))) {}))
             batch-state))
      (is (= (redis-cmds/set-members tu/redis-conn enqueued-job-set) job-ids)))))

;;;; ======= TEST: Batch state update middleware ===========

(defn redis-set-batch-state [conn batch]
  (let [batch-state-key (d/prefix-batch batch-id)
        batch-state (select-keys batch redis-batch/batch-state-keys)]
    (redis-cmds/hmset* conn batch-state-key batch-state)))

(deftest enqueued-to-successful
  (testing "Job state is transitioned from enqueued -> successful on successful job execution"
    (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
    (redis-set-batch-state (:redis-conn opts) batch)
    ;; Call to middleware to update batch state
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
    (redis-set-batch-state (:redis-conn opts) batch)
    (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
    (is (thrown-with-msg? Exception #"Exception"
                          ;; Call to middleware to update batch state
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
      (redis-set-batch-state (:redis-conn opts) batch)
      (redis-cmds/add-to-set (:redis-conn opts) enqueued-job-set job-id)
      (is (thrown-with-msg? Exception #"Exception"
                            ;; Call to middleware to update batch state
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
      (redis-set-batch-state (:redis-conn opts) batch)
      (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)
      ;; Call to middleware to update batch state
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
                         :retry-opts {:max-retries 2})]
      (redis-set-batch-state (:redis-conn opts) batch)
      (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)
      (is (thrown-with-msg? Exception #"Exception"
                            ;; Call to middleware to update batch state
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
                                 :retry-count 0})]
      (redis-set-batch-state (:redis-conn opts) batch)
      (redis-cmds/add-to-set (:redis-conn opts) retry-job-set job-id)
      (is (thrown-with-msg? Exception #"Exception"
                            ;; Call to middleware to update batch state
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

(deftest set-expiration-test
  (testing "Batch state expires after the linger duration has elapsed"
    (let [batch-id (:id batch)
          batch-state-key (d/prefix-batch batch-id)
          linger-sec "5"
          linger-ms (* (Long/parseLong linger-sec) 1000)]
      (redis-batch/enqueue tu/redis-conn batch)
      (is (< (redis-cmds/ttl tu/redis-conn batch-state-key) 0))
      (is (< (redis-cmds/ttl tu/redis-conn enqueued-job-set) 0))
      (is (< (redis-cmds/ttl tu/redis-conn retry-job-set) 0))
      (is (< (redis-cmds/ttl tu/redis-conn successful-job-set) 0))
      (is (< (redis-cmds/ttl tu/redis-conn dead-job-set) 0))

      (redis-batch/set-batch-expiration tu/redis-conn batch-id linger-sec)
      (is (> (redis-cmds/ttl tu/redis-conn batch-state-key) 0))
      (is (> (redis-cmds/ttl tu/redis-conn enqueued-job-set) 0))

      (Thread/sleep ^long linger-ms)
      (is (= (redis-cmds/exists tu/redis-conn batch-state-key) 0))
      (is (= (redis-cmds/exists tu/redis-conn enqueued-job-set) 0))
      (is (= (redis-cmds/exists tu/redis-conn retry-job-set) 0))
      (is (= (redis-cmds/exists tu/redis-conn successful-job-set) 0))
      (is (= (redis-cmds/exists tu/redis-conn dead-job-set) 0)))))
