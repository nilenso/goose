(ns goose.integration.scheduling-test
  (:require
   [goose.integration.test-utils :as tu :refer [executable
                                                delivered-execution
                                                def-integration-test]]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]])
  (:import
   [java.time Instant]))

(def requirements #{:schedule})

(def-integration-test absolute-scheduling-test requirements
  (let [_ (c/perform-at (tu/get-opts broker :client)
                        (Instant/now)
                        `executable
                        test-name
                        ::absolute-scheduling-test)
        scheduler (w/start (tu/get-opts broker :worker))]
    (is (= ::absolute-scheduling-test
           (delivered-execution test-name)))
    (w/stop scheduler)))

(def-integration-test relative-scheduling-test requirements
  (let [_ (c/perform-in-sec (tu/get-opts broker :client)
                            1
                            `executable
                            test-name
                            ::relative-scheduling-test)
        scheduler (w/start (tu/get-opts broker :worker))]
    (is (= ::relative-scheduling-test
           (delivered-execution test-name)))
    (w/stop scheduler)))
