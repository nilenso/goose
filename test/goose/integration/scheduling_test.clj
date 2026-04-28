(ns goose.integration.scheduling-test
  (:require
   [goose.integration.test-utils :as tu :refer [setup-test-promise
                                                executable
                                                delivered-execution]]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]])
  (:import
   [java.time Instant]))

(def requirements #{:schedule})

(deftest  absolute-scheduling-test
  (doseq [broker (keys (tu/get-configs :implementations))]
    (let [test-name (str (symbol broker) "-absolute-scheduling-test")]
      (if (tu/broker-testable? broker requirements)
        (do 
          (setup-test-promise test-name)
          (tu/with-fixtures broker
            (fn [ex] (report {:type :default
                             :message (ex-message ex)}))
            (testing (str "Absolute Scheduling" broker)
              (let [_ (c/perform-at (tu/get-opts broker :client)
                                    (Instant/now)
                                    `executable
                                    test-name
                                    ::absolute-scheduling-test)
                    scheduler (w/start (tu/get-opts broker :worker))]
                (is (= ::absolute-scheduling-test
                       (delivered-execution test-name)))
                (w/stop scheduler)))))
        (report {:type :default
                 :message (str "Absolute Scheduling" broker " is not testable")})))))

(deftest  relative-scheduling-test
  (doseq [broker (keys (tu/get-configs :implementations))]
    (let [test-name (str (symbol broker) "-relative-scheduling-test")]
      (if (tu/broker-testable? broker requirements)
        (do 
          (setup-test-promise test-name)
          (tu/with-fixtures broker
            (fn [ex] (report {:type :default
                             :message (ex-message ex)}))
            (testing (str "Relative Scheduling" broker)
              (let [_ (c/perform-in-sec (tu/get-opts broker :client)
                                        1
                                        `executable
                                        test-name
                                        ::relative-scheduling-test)
                    scheduler (w/start (tu/get-opts broker :worker))]
                (is (= ::relative-scheduling-test
                       (delivered-execution test-name)))
                (w/stop scheduler)))))
        (report {:type :default
                 :message (str "Relative Scheduling" broker " is not testable")})))))
