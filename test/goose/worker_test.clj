(ns goose.worker-test
  (:require
   [goose.client :as c]
   [goose.test-utils :as tu]
   [goose.worker :as w]

   [clojure.test :refer [deftest is testing use-fixtures]]))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/redis-fixture)

;;; ======= TEST: nil metrics-plugin ==========
(def nil-metrics-plugin-fn-executed (atom (promise)))
(defn nil-metrics-plugin-fn [arg]
  (deliver @nil-metrics-plugin-fn-executed arg))

(deftest nil-metrics-plugin-test
  (testing "Worker accepts a nil metrics-plugin"
    (reset! nil-metrics-plugin-fn-executed (promise))
    (let [arg "nil-metrics-plugin-test"
          _ (c/perform-async tu/redis-client-opts `nil-metrics-plugin-fn arg)
          worker-opts (dissoc tu/redis-worker-opts :metrics-plugin)
          worker (w/start worker-opts)]
      (is (= arg (deref @nil-metrics-plugin-fn-executed 100 :nil-metrics-plugin-test-timed-out)))
      (w/stop worker))))
