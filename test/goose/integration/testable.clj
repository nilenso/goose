(ns goose.integration.testable
  (:require
   [clojure.set :as s]))

;; map of test-types and capabilities required
(def test-reqs
  {:async-execution-test
   #{}})

(defn broker-testable?
  "predicate on whether the broker implementation
  is capable enough to execute test-type"
  [broker test-type]
  (s/subset? (get test-reqs test-type)
             (fetch-capabilities broker)))

(comment
  (broker-testable? "Redis" :async-execution-test))
