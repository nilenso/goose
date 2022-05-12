(ns goose.worker-test
  (:require
    [clojure.test :refer :all]
    [goose.worker :as sut]
    [goose.client :as c]))

(def ^:private called-with (atom nil))

(defn placeholder-fn [arg1]
  (println "called")
  (reset! called-with arg1))

(deftest start-stop-test
  ; TODO: Uncomment test once broker URL is configurable.
  (comment
    (testing "executes a function asynchronously"
      (sut/start 1)
      (c/async `placeholder-fn {:args '(123)})
      (sut/stop)
      (is (= 123 @called-with)))))