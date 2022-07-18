(ns goose.api-test
  (:require
    [goose.api.dead-jobs :as dead-jobs]
    [goose.api.enqueued-jobs :as enqueued-jobs]
    [goose.api.init :as init]
    [goose.api.scheduled-jobs :as scheduled-jobs]
    [goose.client :as c]
    [goose.redis :as r]
    [goose.statsd :as statsd]
    [goose.worker :as w]

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
  (clear-redis))

(use-fixtures :once fixture)

(defn bg-fn [id] (inc id))
(deftest enqueued-jobs-test
  (testing "enqueued-jobs APIF"
    (let [job-id (c/perform-async client-opts `bg-fn 1)
          _ (c/perform-async client-opts `bg-fn 2)]
      (is (= (list queue) (enqueued-jobs/list-all-queues)))
      (is (= 2 (enqueued-jobs/size queue)))
      (let [match? (fn [job] (= (list 2) (:args job)))]
        (is (= 1 (count (enqueued-jobs/find-by-pattern queue match?)))))

      (let [job (enqueued-jobs/find-by-id queue job-id)]
        (is (some? (enqueued-jobs/enqueue-front-for-execution queue job)))
        (is (true? (enqueued-jobs/delete queue job))))

      (is (true? (enqueued-jobs/delete-all queue))))))

(deftest scheduled-jobs-test
  (testing "scheduled-jobs API"
    (let [job-id1 (c/perform-in-sec client-opts 10 `bg-fn 1)
          job-id2 (c/perform-in-sec client-opts 10 `bg-fn 2)
          _ (c/perform-in-sec client-opts 10 `bg-fn 3)]
      (is (= 3 (scheduled-jobs/size)))
      (let [match? (fn [job] (not= (list 1) (:args job)))]
        (is (= 2 (count (scheduled-jobs/find-by-pattern match?)))))

      (let [job (scheduled-jobs/find-by-id job-id1)]
        (is (some? (scheduled-jobs/enqueue-front-for-execution job)))
        (is (false? (scheduled-jobs/delete job)))
        (is (true? (enqueued-jobs/delete queue job))))

      (let [job (scheduled-jobs/find-by-id job-id2)]
        (is (true? (scheduled-jobs/delete job))))

      (is (true? (scheduled-jobs/delete-all))))))

(defn death-handler [_ _])
(def dead-fn-atom (atom 0))
(defn dead-fn [id]
  (swap! dead-fn-atom inc)
  (throw (Exception. (str id " died!"))))

(deftest dead-jobs-test
  (testing "dead-jobs API"
    (let [worker (w/start worker-opts)
          job-opts (update-in client-opts [:retry-opts] assoc
                              :max-retries 0
                              :death-handler-fn-sym `death-handler)
          dead-job-id (c/perform-async job-opts `dead-fn -1)
          _ (doseq [id (range 3)] (c/perform-async job-opts `dead-fn id))
          circuit-breaker (atom 0)]
      ; Wait until 4 jobs have died after execution.
      (while (and (> 4 @circuit-breaker) (not= 4 @dead-fn-atom))
        (swap! circuit-breaker inc)
        (Thread/sleep 40))
      (w/stop worker)

      (is (= 4 (dead-jobs/size)))

      (let [dead-job (dead-jobs/find-by-id dead-job-id)]
        (is some? (dead-jobs/enqueue-front-for-execution dead-job))
        (is true? (enqueued-jobs/delete queue dead-job)))

      (let [match? (fn [job] (= (list 0) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern match?)
            dead-at (get-in dead-job [:state :dead-at])]
        (is (true? (dead-jobs/delete-older-than dead-at))))

      (let [match? (fn [job] (= (list 1) (:args job)))
            [dead-job] (dead-jobs/find-by-pattern match?)]
        (is (true? (dead-jobs/delete dead-job))))

      (is (true? (dead-jobs/delete-all))))))
