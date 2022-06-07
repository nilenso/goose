(ns goose.integration-test
  (:require
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.utils :as u]
    [goose.worker :as w]

    [clojure.test :refer [deftest is testing use-fixtures]]
    [goose.scheduler :as scheduler])
  (:import
    [java.util UUID]))

(def redis-url
  (let [host (or (System/getenv "GOOSE_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(def queues
  {:test     "test"
   :retry    "test-retry"
   :schedule d/schedule-queue
   :dead     d/dead-queue})

(def client-opts
  (assoc c/default-opts
    :queue (:test queues)
    :redis-url redis-url))

(def worker-opts
  (assoc w/default-opts
    :redis-url redis-url
    :queue (:test queues)
    :graceful-shutdown-sec 1
    :scheduler-polling-interval-sec 1))

; ======= Setup & Teardown ==========

(defn- clear-redis
  [keys]
  (let [redis-conn (r/conn redis-url {})]
    (r/del-keys redis-conn keys)))

(defn integration-test-fixture [f]
  (let [prefixed-queues (map u/prefix-queue (vals queues))]
    (clear-redis prefixed-queues)
    (f)
    (clear-redis prefixed-queues)))

(use-fixtures :once integration-test-fixture)

; ======= TEST: Async execution ==========
(def fn-called (promise))
(defn placeholder-fn [arg]
  (deliver fn-called arg))

(deftest enqueue-dequeue-execute-test
  (testing "Goose executes a function asynchronously"
    (let [arg "async-execute-test"
          worker (w/start worker-opts)]
      (is (uuid? (UUID/fromString (c/async client-opts `placeholder-fn arg))))
      (is (= arg (deref fn-called 100 :e2e-test-timed-out)))
      (w/stop worker))))

; ======= TEST: Scheduling ==========
(def scheduled-fn-called (promise))
(defn scheduled-fn [arg]
  (deliver scheduled-fn-called arg))

(deftest scheduler-test
  (testing "Goose executes a scheduled function asynchronously"
    (let [arg "scheduling-test"
          scheduled-opts (-> client-opts
                             (scheduler/set-schedule {:perform-in-sec 1}))
          scheduler (w/start worker-opts)]
      (c/async scheduled-opts `scheduled-fn arg)
      (is (= arg (deref scheduled-fn-called 4100 :scheduler-test-timed-out)))
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
(comment
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
        (c/async (assoc client-opts :retry-opts retry-opts) `erroneous-fn arg)

        (is (= java.lang.ArithmeticException (type (deref failed-on-execute 100 :retry-execute-timed-out))))
        (w/stop worker)

        (is (= clojure.lang.ExceptionInfo (type (deref failed-on-1st-retry 4100 :1st-retry-timed-out))))

        (is (= arg (deref succeeded-on-2nd-retry 4100 :2nd-retry-timed-out)))
        (w/stop retry-worker)))))

; ======= TEST: Error handling dead-job using job queue ==========
(def job-dead (promise))
(defn dead-test-error-handler [_ _])
(defn dead-test-death-handler [_ ex]
  (deliver job-dead ex))

(def dead-job-run-count (atom 0))
(defn dead-fn []
  (swap! dead-job-run-count inc)
  (/ 1 0))

(comment
  (deftest dead-test
    (testing "Goose marks a job as dead upon reaching max retries"
      (let [retry-opts (assoc retry/default-opts
                         :max-retries 1
                         :retry-delay-sec-fn-sym `immediate-retry
                         :error-handler-fn-sym `dead-test-error-handler
                         :death-handler-fn-sym `dead-test-death-handler)
            worker (w/start worker-opts)]
        (c/async (assoc client-opts :retry-opts retry-opts) `dead-fn)

        (is (= java.lang.ArithmeticException (type (deref job-dead 4200 :death-handler-timed-out))))
        (is (= 2 @dead-job-run-count))
        (w/stop worker)))))
