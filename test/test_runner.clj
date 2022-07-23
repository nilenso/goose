(ns test-runner
  (:require
    [goose.specs :as specs]
    [cognitect.test-runner.api :as test-runner]))

(defn enable-specs [] (specs/instrument))

(defn test-and-shutdown
  [_]
  (enable-specs)
  (test-runner/test ["-m" "cognitect.test-runner"])

  ; clj-statsd uses agents.
  ; If not shutdown, program won't quit.
  ; https://stackoverflow.com/questions/38504056/program-wont-end-when-using-clj-statsd
  (shutdown-agents))
