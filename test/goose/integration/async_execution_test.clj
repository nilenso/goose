(ns goose.integration.async-execution-test
  (:require
   [goose.integration.test-utils :as tu :refer [setup-test-promise
                                                executable
                                                delivered-execution]]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]]))

(def requirements #{:enqueue})

(deftest  async-execution-test
  (doseq [broker (keys (tu/get-configs :implementations))]
    (let [test-name (str (symbol broker) "-async-execution-test")]
      (if (tu/broker-testable? broker requirements)
        (do
          (setup-test-promise test-name)
          (tu/with-fixture broker
            (fn [ex] (report {:type :default
                              :message (ex-message ex)}))
            (testing (str "Async Execution" broker)
              (let [_ (c/perform-async (tu/get-opts broker :client)
                                       `executable
                                       test-name
                                       ::async-execution-test)
                    worker (w/start (tu/get-opts broker :worker))]
                (is (= ::async-execution-test
                       (delivered-execution test-name)))
                (w/stop worker)))))
        (report {:type :default
                 :message (str "Async execution" broker " is not testable")})))))
