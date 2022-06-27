(ns goose.integration-test
  (:require
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]]
    [goose.broker :as broker]
    [goose.retry :as retry])
  (:import
    [java.util UUID]))

(def redis-url
  (let [host (or (System/getenv "GOOSE_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(def broker-opts
  (assoc broker/default-opts
    :redis-url redis-url))

(def queues
  {:test     "test"
   :retry    "test-retry"
   :schedule d/schedule-queue
   :dead     d/dead-queue})

(def client-opts
  {:queue       (:test queues)
   :broker-opts broker-opts})

(def worker-opts
  {:threads                        1
   :broker-opts                    broker-opts
   :queue                          (:test queues)
   :graceful-shutdown-sec          1
   :scheduler-polling-interval-sec 1})

; ======= Setup & Teardown ==========

(defn- clear-redis
  [keys]
  (let [redis-conn (r/conn {:redis-url redis-url})]
    (r/del-keys redis-conn keys)))

(defn integration-test-fixture [f]
  (let [prefixed-queues (map d/prefix-queue (vals queues))]
    (clear-redis prefixed-queues)
    (f)
    (clear-redis prefixed-queues)))

(use-fixtures :once integration-test-fixture)

; ======= TEST: Async execution ==========
(def perform-async-fn-executed (promise))
(defn perform-async-fn [arg]
  (deliver perform-async-fn-executed arg))

(deftest perform-async-test
  (testing "Goose executes a function asynchronously"
    (let [arg "async-execute-test"
          worker (w/start worker-opts)]
      (is (uuid? (UUID/fromString (c/perform-async client-opts `perform-async-fn arg))))
      (is (= arg (deref perform-async-fn-executed 100 :e2e-test-timed-out)))
      (w/stop worker))))

; ======= TEST: Relative Scheduling ==========
(def perform-in-sec-fn-executed (promise))
(defn perform-in-sec-fn [arg]
  (deliver perform-in-sec-fn-executed arg))

(deftest perform-in-sec-test
  (testing "Goose executes a function scheduled in future"
    (let [arg "scheduling-test"
          _ (c/perform-in-sec client-opts 1 `perform-in-sec-fn arg)
          scheduler (w/start worker-opts)]
      (is (= arg (deref perform-in-sec-fn-executed 4100 :scheduler-test-timed-out)))
      (w/stop scheduler))))

; ======= TEST: Absolute Scheduling (in-past) ==========
(def perform-at-fn-executed (promise))
(defn perform-at-fn [arg]
  (deliver perform-at-fn-executed arg))

(deftest perform-at-test
  (testing "Goose executes a function scheduled in past"
    (let [arg "scheduling-test"
          _ (c/perform-at client-opts (java.util.Date.) `perform-at-fn arg)
          scheduler (w/start worker-opts)]
      (is (= arg (deref perform-at-fn-executed 100 :scheduler-test-timed-out)))
      (w/stop scheduler))))


; ======= TEST: Error handling transient failure job using custom retry queue ==========
(defn immediate-retry [_] 1)

(def failed-on-execute (promise))
(def failed-on-1st-retry (promise))
(def succeeded-on-2nd-retry (promise))

(defn retry-test-error-handler [_ ex]
  (if (realized? failed-on-execute)
    (deliver failed-on-1st-retry ex)
    (deliver failed-on-execute ex)))

(defn erroneous-fn [arg]
  (when-not (realized? failed-on-execute)
    (/ 1 0))
  (when-not (realized? failed-on-1st-retry)
    (throw (ex-info "error" {})))
  (deliver succeeded-on-2nd-retry arg))
(deftest retry-test
  (testing "Goose retries an errorneous function"
    (let [arg "retry-test"
          retry-opts (assoc retry/default-opts
                       :max-retries 2
                       :retry-delay-sec-fn-sym `immediate-retry
                       :retry-queue (:retry queues)
                       :error-handler-fn-sym `retry-test-error-handler)
          worker (w/start worker-opts)
          retry-worker (w/start (assoc worker-opts :queue (:retry queues)))]
      (c/perform-async (assoc client-opts :retry-opts retry-opts) `erroneous-fn arg)

      (is (= java.lang.ArithmeticException (type (deref failed-on-execute 100 :retry-execute-timed-out))))
      (w/stop worker)

      (is (= clojure.lang.ExceptionInfo (type (deref failed-on-1st-retry 4100 :1st-retry-timed-out))))

      (is (= arg (deref succeeded-on-2nd-retry 4100 :2nd-retry-timed-out)))
      (w/stop retry-worker))))

; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (promise))
(defn dead-test-error-handler [_ _])
(defn dead-test-death-handler [_ ex]
  (deliver job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn []
  (swap! dead-job-run-count inc)
  (/ 1 0))

(deftest dead-test
  (testing "Goose marks a job as dead upon reaching max retries"
    (let [dead-job-opts (assoc retry/default-opts
                          :max-retries 1
                          :retry-delay-sec-fn-sym `immediate-retry
                          :error-handler-fn-sym `dead-test-error-handler
                          :death-handler-fn-sym `dead-test-death-handler)
          worker (w/start worker-opts)]
      (c/perform-async (assoc client-opts :retry-opts dead-job-opts) `dead-fn)

      (is (= java.lang.ArithmeticException (type (deref job-dead 4200 :death-handler-timed-out))))
      (is (= 2 @dead-job-run-count))
      (w/stop worker))))
