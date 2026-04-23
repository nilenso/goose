(ns goose.integration.async-execution-test
  (:require
   [goose.integration.test-utils :as tu]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]]))

(def requirements #{:enqueue})

(def perform-async-fn-executed (atom nil))

(defn perform-async-fn [arg]
  (reset! perform-async-fn-executed arg))

(deftest  async-execution-test
  (doseq [broker (keys tu/broker-utils)]
    (alter-meta! #'async-execution-test assoc :name (str (symbol broker) "-async-execution-test"))
    (reset! perform-async-fn-executed nil)
    (if (tu/broker-testable? broker requirements)
      (tu/with-fixtures broker
        (fn [ex] (report {:type :default
                          :message (ex-message ex)}))
        (testing (str "Async Execution" broker)
          (let [_ (c/perform-async (tu/get-opts broker :client)
                                   `perform-async-fn
                                   ::async-execution-test)
                worker (w/start (tu/get-opts broker :worker))]
            (Thread/sleep 100)
            (is (= ::async-execution-test @perform-async-fn-executed))
            (w/stop worker))))
      (report {:type :default
               :message (str "Async execution" broker " is not testable")}))))
