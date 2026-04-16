(ns goose.integration.test-utils
  (:require
   [goose.test-utils :as tu]
   [goose.capability :as c]
   [clojure.set :as s]
   [clojure.test :refer [deftest use-fixtures testing is]]))

;; map of test-types and capabilities required
(def test-reqs
  {:async-execution-test
   #{}})

;; TODO : direct index leveraging older boilerplate rn
;; refactor potential unexplored yet

;; per broker utilities
(def broker-utils
  {:redis {:fixtures {:each [tu/redis-fixture]
                      :once []}
           :client-opts tu/redis-client-opts
           :worker-opts tu/redis-worker-opts}
   :rabbitmq {:fixtures {:each [tu/rmq-fixture]
                         :once []}
              :client-opts tu/rmq-client-opts
              :worker-opts tu/rmq-worker-opts}})

(defmacro switch-ns [broker test-type]
  `(ns ~(symbol  (str broker "-" test-type))
     (:require [~(symbol (str  "goose.integration." test-type)) :refer :all])))

(comment
  (switch-ns "redis" "async-execution-test")
  )

(defmacro register-fixtures
  "registers :each and :once fixtures in the current test namespace"
  [broker]
  (letfn [(fetch-fixtures  [type]
            (->> broker
                 (keyword)
                 (get broker-utils)
                 (:fixtures)
                 (type)))]
    `(do
       ~@(for [type [:each :once]]
           `(use-fixtures ~type ~@(fetch-fixtures type))))))

(comment
  (register-fixtures "redis"))

(defmacro  setup-test-environment [broker test-type]
  `(do (switch-ns ~broker ~test-type)
       (register-fixtures ~broker)))


(defmacro gen-test-suite [test-type test-generator]
  `(do
     ~@(for [broker (keys broker-utils)]
         (when (broker-testable? broker test-type)
           (test-generator broker)))))

(comment
  (setup-test-environment "redis" "async-execution-test")
  )

(defn broker-testable?
  "predicate on whether the broker implementation
  is capable enough to execute test-type"
  [broker test-type]
  (s/subset? (get test-reqs test-type)
             (c/fetch-capabilities broker)))


(comment
  (broker-testable? "Redis" :async-execution-test))
