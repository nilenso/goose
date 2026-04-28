(ns goose.integration.async-execution-test
  (:require
   [goose.integration.test-utils :as tu :refer [executable
                                                delivered-execution
                                                def-integration-test]]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.test :refer [deftest is testing report]]))

(def requirements #{:enqueue})

(def-integration-test async-execution-test
  requirements
  (let [_ (c/perform-async (tu/get-opts broker :client)
                           `executable
                           test-name
                           ::async-execution-test)
        worker (w/start (tu/get-opts broker :worker))]
    (is (= ::async-execution-test
           (delivered-execution test-name)))
    (w/stop worker)))
