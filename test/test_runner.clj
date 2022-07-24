(ns test-runner
  (:require [cognitect.test-runner.api :as test-runner]))

(defn test-and-shutdown
  [_]
  (test-runner/test ["-m" "cognitect.test-runner"])

  ; clj-statsd uses agents.
  ; If not shutdown, program won't quit.
  ; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))
