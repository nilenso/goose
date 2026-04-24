(ns goose.integration.scheduling-test
  (:require
   [goose.integration.test-utils :as tu]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]])
  (:import
   [java.time Instant]))

(def requirements #{:schedule})

(def execd-log (atom {}))

(defn perform-scheduled-fn [test-name executed-flag]
  (swap! execd-log assoc test-name executed-flag))

(deftest  absolute-scheduling-test
  (doseq [broker (keys tu/broker-utils)]
    (let [test-name (str (symbol broker) "-absolute-scheduling-test")]
      (alter-meta! #'absolute-scheduling-test assoc :name test-name)
      (if (tu/broker-testable? broker requirements)
        (tu/with-fixtures broker
          (fn [ex] (report {:type :default
                            :message (ex-message ex)}))
          (testing (str "Absolute Scheduling" broker)
            (let [test-flag (random-uuid)
                  _ (c/perform-at (tu/get-opts broker :client)
                                  (Instant/now)
                                  `perform-scheduled-fn
                                  test-name
                                  test-flag)
                  scheduler (w/start (tu/get-opts broker :worker))]
              (Thread/sleep 100)
              (is (= test-flag (get @execd-log test-name)))
              (w/stop scheduler))))
        (report {:type :default
                 :message (str "Absolute Scheduling" broker " is not testable")})))))

(deftest  relative-scheduling-test
  (doseq [broker (keys tu/broker-utils)]
    (let [test-name (str (symbol broker) "-relative-scheduling-test")]
      (alter-meta! #'relative-scheduling-test assoc :name test-name)
      (if (tu/broker-testable? broker requirements)
        (tu/with-fixtures broker
          (fn [ex] (report {:type :default
                            :message (ex-message ex)}))
          (testing (str "Relative Scheduling" broker)
            (let [test-flag (random-uuid)
                  _ (c/perform-in-sec (tu/get-opts broker :client)
                                      1
                                      `perform-scheduled-fn
                                      test-name
                                      test-flag)
                  scheduler (w/start (tu/get-opts broker :worker))]
              (Thread/sleep 2100)
              (is (= test-flag (get @execd-log test-name)))
              (w/stop scheduler))))
        (report {:type :default
                 :message (str "Relative Scheduling" broker " is not testable")})))))
