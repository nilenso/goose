(ns test-runner
  (:require
    [cognitect.test-runner.api :as test-runner]))

(defn test-and-shutdown
  [args]
  (let [test-params (array-map :-m "cognitect.test-runner")]
    (test-runner/test (merge test-params args)))

  ; clj-statsd uses agents.
  ; If not shutdown, program won't quit.
  ; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))
