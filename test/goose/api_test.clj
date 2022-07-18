(ns goose.api-test
  (:require
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.api.init :as init]
    [goose.api.scheduled-jobs :as scheduled-jobs]
    [goose.client :as c]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.worker :as w]


    [clojure.test :refer [deftest is testing use-fixtures]]
    [taoensso.carmine :as car]
    [goose.statsd :as statsd]))

(def redis-url
  (let [host (or (System/getenv "GOOSE_TEST_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_TEST_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(def broker-opts
  {:redis {:redis-url redis-url}})

(def queue "api-test")
(def client-opts
  {:queue       queue
   :broker-opts broker-opts})
(def worker-opts
  {:threads                        1
   :broker-opts                    broker-opts
   :queue                          queue
   :graceful-shutdown-sec          1
   :scheduler-polling-interval-sec 1
   :statsd-opts                    (assoc statsd/default-opts :disabled? true)})

(defn- clear-redis []
  (let [redis-conn (r/conn (:redis broker-opts))]
    (r/wcar* redis-conn (car/flushdb "sync"))))

(defn- fixture
  [f]
  (clear-redis)
  (init/initialize broker-opts)
  (f)
  ;(clear-redis)
  )

(use-fixtures :once fixture)

(defn bg-fn1 [])

(defn bg-fn2 [])
(defn match?
  [job]
  (= `bg-fn2 (:execute-fn-sym job)))

(deftest enqueued-jobs-test
  (testing "enqueued-jobs APIF"
    (let [job-id (c/perform-async client-opts `bg-fn1)
          job (enqueued-jobs/find-by-id queue job-id)

          _ (c/perform-async client-opts `bg-fn2)]
      (is (= (list queue) (enqueued-jobs/list-all-queues)))
      (is (= 2 (enqueued-jobs/size queue)))
      (is (= 1 (count (enqueued-jobs/find-by-pattern queue match?))))

      (is (some? (enqueued-jobs/enqueue-front-for-execution queue job)))

      (is (true? (enqueued-jobs/delete queue job)))
      (is (true? (enqueued-jobs/delete-all queue))))))

(deftest scheduled-jobs-test
  (testing "scheduled-jobs API"
    (let [job-id1 (c/perform-in-sec client-opts 10 `bg-fn1)
          job1 (scheduled-jobs/find-by-id job-id1)

          job-id2 (c/perform-in-sec client-opts 10 `bg-fn2)
          job2 (scheduled-jobs/find-by-id job-id2)
          _ (c/perform-in-sec client-opts 10 `bg-fn2)]
      (is (= 3 (scheduled-jobs/size)))
      (is (= 2 (count (scheduled-jobs/find-by-pattern match?))))

      (is (some? (scheduled-jobs/enqueue-front-for-execution job1)))
      (is (false? (scheduled-jobs/delete job1)))
      (is (true? (enqueued-jobs/delete queue job1)))

      (is (true? (scheduled-jobs/delete job2)))
      (is (true? (scheduled-jobs/delete-all))))))

(defn dead-test-error-handler [_ _])
(def dead-fn1-executed (promise))
(defn dead-fn1 []
  (deliver dead-fn1-executed true)
  (throw (Exception. "i'll die")))
(def dead-fn2-executed (promise))
(defn dead-fn2 []
  (deliver dead-fn2-executed true)
  (throw (Exception. "i'll die")))

(defn dead-jobs-match?
  [job]
  (= `dead-fn2 (:execute-fn-sym job)))

(def dead-job-opts
  (assoc retry/default-opts
    :max-retries 0
    :death-handler-fn-sym `dead-test-error-handler))

(deftest dead-jobs-test
  (testing "dead-jobs API"
    (let [worker (w/start worker-opts)
          dead-job-id1 (c/perform-async (assoc client-opts :retry-opts dead-job-opts) `dead-fn1)
          dead-job-id2 (c/perform-async (assoc client-opts :retry-opts dead-job-opts) `dead-fn2)]
      (is (true? (deref dead-fn1-executed 100 :api-test-timed-out)))
      (is (true? (deref dead-fn2-executed 10 :api-test-timed-out)))
      (w/stop worker)
      (is (= 2 (dead-jobs/size)))

      (let [dead-job1 (dead-jobs/find-by-id dead-job-id1)]
        (is some? (dead-jobs/enqueue-front-for-execution dead-job1)))
      (let [[dead-job2] (dead-jobs/find-by-pattern dead-jobs-match?)]
        (is (true? (dead-jobs/delete dead-job2)))))))
