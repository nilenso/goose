(ns goose.integration.scheduling-test
  (:require
   [goose.integration.test-utils :as tu]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]])
  (:import
   [java.time Instant]))


(def requirements #{:schedule})

(def perform-scheduled-fn-executed (atom nil))

(defn perform-scheduled-fn [arg]
  (reset! perform-scheduled-fn-executed arg))

(deftest  absolute-scheduling-test
  (doseq [broker (keys tu/broker-utils)]
    (alter-meta! #'absolute-scheduling-test assoc :name (str (symbol broker) "-absolute-scheduling-test"))
    (reset! perform-scheduled-fn-executed nil)
    (if (tu/broker-testable? broker requirements)
      (tu/with-fixtures broker
        (fn [ex] (report {:type :default
                         :message (ex-message ex)}))
        (testing (str "Absolute Scheduling" broker)
          (let [_ (c/perform-at (tu/get-opts broker :client)
                                (Instant/now)
                                `perform-scheduled-fn
                                ::absolute-scheduling-test)
                scheduler (w/start (tu/get-opts broker :worker))]
            (Thread/sleep 100)
            (is (= ::absolute-scheduling-test @perform-scheduled-fn-executed))
            (w/stop scheduler))))
      (report {:type :default
               :message (str "Absolute Scheduling" broker " is not testable")}))))


(deftest  relative-scheduling-test
  (doseq [broker (keys tu/broker-utils)]
    (alter-meta! #'relative-scheduling-test assoc :name (str (symbol broker) "-relative-scheduling-test"))
    (reset! perform-scheduled-fn-executed nil)
    (if (tu/broker-testable? broker requirements)
      (tu/with-fixtures broker
        (fn [ex] (report {:type :default
                         :message (ex-message ex)}))
        (testing (str "Relative Scheduling" broker)
          (let [_ (c/perform-in-sec (tu/get-opts broker :client)
                                    1
                                    `perform-scheduled-fn
                                    ::relative-scheduling-test)
                scheduler (w/start (tu/get-opts broker :worker))]
            (Thread/sleep 2100)
            (is (= ::relative-scheduling-test @perform-scheduled-fn-executed))
            (w/stop scheduler))))
      (report {:type :default
               :message (str "Relative Scheduling" broker " is not testable")}))))
