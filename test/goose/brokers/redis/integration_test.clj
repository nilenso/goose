(ns goose.brokers.redis.integration-test
  (:require
   [goose.batch :as batch]
   [goose.brokers.redis.batch :as redis-batch]
   [goose.brokers.redis.commands :as redis-cmds]
   [goose.brokers.redis.consumer :as redis-consumer]
   [goose.client :as c]
   [goose.defaults :as d]
   [goose.job :as j]
   [goose.retry :as retry]
   [goose.test-utils :as tu]
   [goose.utils :as u]
   [goose.worker :as w]

   [clojure.test :refer [deftest is testing use-fixtures]]
   [taoensso.carmine :as car])
  (:import
   [java.util UUID]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ======= TEST: Orphan job recovery ==========
(def orphan-job-recovered (atom (promise)))
(defn orphaned-job-fn [arg]
  (deliver @orphan-job-recovered arg))
(deftest orphan-job-recovery-test
  (testing "[redis] Goose recovers an orphan job"
    (let [dead-worker-id (str tu/queue ":" (u/hostname) ":" "random")
          arg "orphan-checker-test"
          ready-queue (d/prefix-queue tu/queue)
          orphaned-job (j/new `orphaned-job-fn (list arg) tu/queue ready-queue retry/default-opts)
          process-set (str d/process-prefix tu/queue)
          preservation-queue (redis-consumer/preservation-queue dead-worker-id)]
      ;; Add dead-worker-id to process-set for "test" queue.
      (redis-cmds/add-to-set tu/redis-conn process-set dead-worker-id)
      ;; Simulate orphan-job by pushing it to dead worker's preservation queue.
      (redis-cmds/enqueue-back tu/redis-conn preservation-queue orphaned-job)

      (let [orphan-checker (w/start tu/redis-worker-opts)]
        (is (= arg (deref @orphan-job-recovered 100 :orphan-checker-test-timed-out)))
        (w/stop orphan-checker)))))

;;; ======= TEST: Batch-jobs execution ==========
(defn immediate-retry [_] 0)

(def dead-job-run-count (atom 0))
(defn dead-fn [_]
  (swap! dead-job-run-count inc)
  (/ 1 0))

(def batch-arg-1 :foo)
(def batch-arg-2 :bar)
(def n-jobs-batch-args-sum (atom 0))
(defn n-jobs-batch-fn [arg]
  ;; For a batch of n jobs, this function
  ;; maintains sum of all args for assertion in test.
  (swap! n-jobs-batch-args-sum (fn [n] (+ n arg))))
(def batch-fail-pass-count (atom 0))
(defn batch-job-fail-pass [_]
  (swap! batch-fail-pass-count inc)
  ;; For a batch of 2 jobs, this function
  ;; fails on first execution & succeeds on next attempt.
  (when (> 3 @batch-fail-pass-count) (/ 1 0)))
(defn batch-job-partial-success [arg]
  ;; For a batch of 2 jobs, this function
  ;; always fails for 1st arg & succeeds for 2nd arg.
  (when (= batch-arg-1 arg) (/ 1 0)))
(def callback-fn-executed (atom (promise)))
(defn batch-callback [id status]
  (deliver @callback-fn-executed {:id id :status status}))
(defmacro assert-batch-expiration [id]
  `(let [foo# (redis-batch/batch-keys ~id)]
     (is (= -2 (redis-cmds/wcar* tu/redis-conn (car/ttl (:enqueued-set foo#)))))
     (is (= -2 (redis-cmds/wcar* tu/redis-conn (car/ttl (:retrying-set foo#)))))

     (is (not= -1 (redis-cmds/wcar* tu/redis-conn (car/ttl (:batch-hash foo#)))))
     (is (not= -1 (redis-cmds/wcar* tu/redis-conn (car/ttl (:success-set foo#)))))
     (is (not= -1 (redis-cmds/wcar* tu/redis-conn (car/ttl (:dead-set foo#)))))))

(deftest perform-batch-test
  (let [shared-args (-> []
                        (batch/construct-args batch-arg-1)
                        (batch/construct-args batch-arg-2))
        linger-sec 1
        batch-opts {:callback-fn-sym `batch-callback
                    :linger-sec      linger-sec}]
    (testing "[redis][batch-jobs] Enqueued -> Success"
      (reset! callback-fn-executed (promise))
      (reset! n-jobs-batch-args-sum 0)
      (let [n-args (range 1 20)
            batch-args (map list n-args)
            batch-id (:id (c/perform-batch tu/redis-client-opts batch-opts `n-jobs-batch-fn batch-args))
            worker (w/start tu/redis-worker-opts)]
        (is (uuid? (UUID/fromString batch-id)))
        (is (= (deref @callback-fn-executed 400 :n-jobs-batch-callback-timed-out)
               {:id batch-id :status batch/status-success}))
        (is (= (reduce + n-args) @n-jobs-batch-args-sum))
        (assert-batch-expiration batch-id)
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Retrying -> Success"
      (reset! callback-fn-executed (promise))
      (reset! batch-fail-pass-count 0)
      (let [client-opts (assoc-in tu/redis-client-opts [:retry-opts :retry-delay-sec-fn-sym] `immediate-retry)
            batch-id (:id (c/perform-batch client-opts batch-opts `batch-job-fail-pass shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 2100 :fail-pass-batch-callback-timed-out)
               {:id batch-id :status batch/status-success}))
        (is (= 4 @batch-fail-pass-count))
        (assert-batch-expiration batch-id)
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Retrying -> Dead"
      (reset! callback-fn-executed (promise))
      (reset! dead-job-run-count 0)
      (let [client-opts (update-in tu/redis-client-opts [:retry-opts]
                                   assoc :max-retries 1 :retry-delay-sec-fn-sym `immediate-retry)
            batch-id (:id (c/perform-batch client-opts batch-opts `dead-fn shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 2100 :dead-batch-callback-timed-out)
               {:id batch-id :status batch/status-dead}))
        (is (= 4 @dead-job-run-count))
        (assert-batch-expiration batch-id)
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Dead"
      (reset! callback-fn-executed (promise))
      (reset! dead-job-run-count 0)
      (let [client-opts (assoc-in tu/redis-client-opts [:retry-opts :max-retries] 0)
            batch-id (:id (c/perform-batch client-opts batch-opts `dead-fn shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 400 :dead-batch-callback-timed-out)
               {:id batch-id :status batch/status-dead}))
        (is (= 2 @dead-job-run-count))
        (assert-batch-expiration batch-id)
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Success/Dead -> Partial Success"
      (reset! callback-fn-executed (promise))
      (let [client-opts (assoc-in tu/redis-client-opts [:retry-opts :max-retries] 0)
            batch-id (:id (c/perform-batch client-opts batch-opts `batch-job-partial-success shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 400 :partial-success-batch-callback-timed-out)
               {:id batch-id :status batch/status-partial-success}))
        (assert-batch-expiration batch-id)
        (w/stop worker)))))
