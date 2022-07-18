(ns goose.api-test
  (:require
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.api.init :as init]
    [goose.client :as c]
    [goose.redis :as r]

    [clojure.test :refer [deftest is testing use-fixtures]]
    [taoensso.carmine :as car]))

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
