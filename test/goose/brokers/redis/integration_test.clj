(ns goose.brokers.redis.integration-test
  (:require
    [goose.api.batch :as batch-api]
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
    [goose.batch :as batch])
  (:import
    [clojure.lang ExceptionInfo]
    [java.time Instant]
    [java.util UUID]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ======= TEST: Async execution ==========
(def perform-async-fn-executed (atom (promise)))
(defn perform-async-fn [arg]
  (deliver @perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "[redis] Goose executes a function asynchronously"
    (reset! perform-async-fn-executed (promise))
    (let [arg "async-execute-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-async tu/redis-client-opts `perform-async-fn arg)))))
          worker (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-async-fn-executed 100 :e2e-test-timed-out)))
      (w/stop worker))))

;;; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (atom (promise)))
(defn perform-at-fn [arg]
  (deliver @perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "[redis] Goose executes a function scheduled in past"
    (reset! perform-at-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-at tu/redis-client-opts (Instant/now) `perform-at-fn arg)))))
          scheduler (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-at-fn-executed 100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

;;; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (atom (promise)))
(defn perform-in-sec-fn [arg]
  (deliver @perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "[redis] Goose executes a function scheduled in future"
    (reset! perform-in-sec-fn-executed (promise))
    (let [arg "scheduling-test"
          _ (is (uuid? (UUID/fromString (:id (c/perform-in-sec tu/redis-client-opts 1 `perform-in-sec-fn arg)))))
          scheduler (w/start tu/redis-worker-opts)]
      (is (= arg (deref @perform-in-sec-fn-executed 2100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

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

;;; ======= TEST: Middleware ==========
(def middleware-called (atom (promise)))
(defn add-five [arg] (+ 5 arg))
(defn test-middleware
  [next]
  (fn [opts job]
    (let [result (next opts job)]
      (deliver @middleware-called result))))

(deftest middleware-test
  (testing "[redis] Goose calls middleware"
    (reset! middleware-called (promise))
    (let [worker (w/start (assoc tu/redis-worker-opts
                            :middlewares test-middleware))
          _ (c/perform-async tu/redis-client-opts `add-five 5)]
      (is (= 10 (deref @middleware-called 100 :middleware-test-timed-out)))
      (w/stop worker))))

;;; ======= TEST: Error handling transient failure job using custom retry queue ==========
(def retry-queue "test-retry")
(defn immediate-retry [_] 0)

(def failed-on-execute (atom (promise)))
(def failed-on-1st-retry (atom (promise)))
(def succeeded-on-2nd-retry (atom (promise)))
(def retry-error-service (atom (promise)))

(defn retry-test-error-handler
  [config _ ex]
  (deliver @retry-error-service config)
  (if (realized? @failed-on-execute)
    (deliver @failed-on-1st-retry ex)
    (deliver @failed-on-execute ex)))

(defn erroneous-fn
  [arg]
  (when-not (realized? @failed-on-execute)
    (/ 1 0))
  (when-not (realized? @failed-on-1st-retry)
    (throw (ex-info "error" {})))
  (deliver @succeeded-on-2nd-retry arg))
(deftest retry-test
  (testing "[redis] Goose retries an erroneous function"
    (reset! failed-on-execute (promise))
    (reset! failed-on-1st-retry (promise))
    (reset! succeeded-on-2nd-retry (promise))
    (reset! retry-error-service (promise))

    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue retry-queue
                       :error-handler-fn-sym `retry-test-error-handler)
          _ (c/perform-async (assoc tu/redis-client-opts :retry-opts retry-opts) `erroneous-fn arg)
          error-svc-cfg :my-retry-test-config
          worker-opts (assoc tu/redis-worker-opts :error-service-config error-svc-cfg)
          worker (w/start worker-opts)
          retry-worker (w/start (assoc worker-opts :queue retry-queue))]
      (is (= ArithmeticException (type (deref @failed-on-execute 100 :retry-execute-timed-out))))
      (is (= error-svc-cfg (deref @retry-error-service 1 :retry-error-svc-cfg-timed-out)))
      (w/stop worker)

      (is (= ExceptionInfo (type (deref @failed-on-1st-retry 2100 :1st-retry-timed-out))))

      (is (= arg (deref @succeeded-on-2nd-retry 2100 :2nd-retry-timed-out)))
      (w/stop retry-worker))))

;;; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (atom (promise)))
(def death-error-service (atom (promise)))

(defn dead-test-error-handler [_ _ _])
(defn dead-test-death-handler
  [config _ ex]
  (deliver @death-error-service config)
  (deliver @job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn [_]
  (swap! dead-job-run-count inc)
  (/ 1 0))

(deftest death-test
  (testing "[redis] Goose marks a job as dead upon reaching max retries"
    (reset! job-dead (promise))
    (reset! death-error-service (promise))
    (reset! dead-job-run-count 0)
    (let [dead-job-opts (assoc retry/default-opts
                          :max-retries 1
                          :retry-delay-sec-fn-sym `immediate-retry
                          :error-handler-fn-sym `dead-test-error-handler
                          :death-handler-fn-sym `dead-test-death-handler)
          _ (c/perform-async (assoc tu/redis-client-opts :retry-opts dead-job-opts) `dead-fn :foo)
          error-svc-cfg :my-death-test-config
          worker-opts (assoc tu/redis-worker-opts :error-service-config error-svc-cfg)
          worker (w/start worker-opts)]
      (is (= ArithmeticException (type (deref @job-dead 2100 :death-handler-timed-out))))
      (is (= error-svc-cfg (deref @death-error-service 1 :death-error-svc-cfg-timed-out)))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))

;;; ======= TEST: Batch-jobs execution ==========
(def batch-arg-1 :foo)
(def n-jobs-batch-args-sum (atom 0))
(defn n-jobs-batch-fn [arg]
  ; For a batch of n jobs, this function
  ; maintains sum of all args for assertion in test.
  (swap! n-jobs-batch-args-sum (fn [n] (+ n arg))))
(def batch-fail-pass-count (atom 0))
(defn batch-job-fail-pass [_]
  (swap! batch-fail-pass-count inc)
  ; For a batch of 2 jobs, this function
  ; fails on first execution & succeeds on next attempt.
  (when (> 3 @batch-fail-pass-count) (/ 1 0)))
(defn batch-job-partial-success [arg]
  ; For a batch of 2 jobs, this function
  ; always fails for 1st arg & succeeds for 2nd arg.
  (when (= batch-arg-1 arg) (/ 1 0)))
(def callback-fn-executed (atom (promise)))
(defn batch-callback [id status]
  (deliver @callback-fn-executed {:id id :status status}))

(deftest perform-batch-test
  (let [shared-args [(list batch-arg-1) (list :bar)]
        linger-sec 1
        batch-opts {:callback-fn-sym `batch-callback
                    :linger-sec      linger-sec}]
    (testing "[redis][batch-jobs] Enqueued -> Successful"
      (reset! callback-fn-executed (promise))
      (reset! n-jobs-batch-args-sum 0)
      (let [n-args (range 1 20)
            batch-args (map list n-args)
            batch-id (:id (c/perform-batch tu/redis-client-opts batch-opts `n-jobs-batch-fn batch-args))
            worker (w/start tu/redis-worker-opts)]
        (is (uuid? (UUID/fromString batch-id)))
        (is (= (deref @callback-fn-executed 400 :n-jobs-batch-callback-timed-out)
               {:id batch-id :status batch/status-success}))
        (is (not-empty (batch-api/status tu/redis-producer batch-id)))
        (u/sleep linger-sec 1)
        (is (empty? (batch-api/status tu/redis-producer batch-id)))
        (is (= (reduce + n-args) @n-jobs-batch-args-sum))
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Retrying -> Successful"
      (reset! callback-fn-executed (promise))
      (reset! batch-fail-pass-count 0)
      (let [client-opts (assoc-in tu/redis-client-opts [:retry-opts :retry-delay-sec-fn-sym] `immediate-retry)
            batch-id (:id (c/perform-batch client-opts batch-opts `batch-job-fail-pass shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 2100 :fail-pass-batch-callback-timed-out)
               {:id batch-id :status batch/status-success}))
        (is (= 4 @batch-fail-pass-count))
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
        (w/stop worker)))

    (testing "[redis][batch-jobs] Enqueued -> Success/Dead -> Partial Successful"
      (reset! callback-fn-executed (promise))
      (let [client-opts (assoc-in tu/redis-client-opts [:retry-opts :max-retries] 0)
            batch-id (:id (c/perform-batch client-opts batch-opts `batch-job-partial-success shared-args))
            worker (w/start tu/redis-worker-opts)]
        (is (= (deref @callback-fn-executed 400 :partial-success-batch-callback-timed-out)
               {:id batch-id :status batch/status-partial-success}))
        (w/stop worker)))))
