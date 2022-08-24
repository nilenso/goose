(ns test-runner
  (:require
    [goose.test-utils :as tu]
    [cognitect.test-runner.api :as test-runner]))

(defn test-and-shutdown
  [args]
  (let [test-params (array-map :-m "cognitect.test-runner")]
    (test-runner/test (merge test-params args)))
  (tu/exit-cli))
