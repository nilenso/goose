(ns goose.integration.sentinel-test
  (:require
   [clojure.test :refer [deftest testing is]]))

(def brokers #{"redis" "rmq"})

(defmacro gen-broker-tests [test-type & test-body]
  `(do
     ~@(for [broker ["redis" "rmq" "sqs"]]
         (let [test-name (symbol (str broker "-" test-type))]
           `(deftest ~test-name
              (testing (str ~test-type " : " ~broker)
                (let [~'broker ~broker] 
                  ~@test-body)))))))

;; linter throws err on broker not being resolvable
;; (gen-broker-tests "abstract-execution-test"
;;                   (is (brokers broker)))
