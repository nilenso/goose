(ns goose.integration.test-utils
  (:require
   [goose.test-utils :as tu]
   [goose.capability :as c]
   [clojure.set :as s]))

;; map of test-types and capabilities required
(def test-reqs
  {:async-execution-test
   #{}})


;; TODO : direct index leveraging older boilerplate rn
;; refactor potential unexplored 
;; per broker utilities

(def broker-utils
  {:Redis {:fixtures {:each [tu/redis-fixture]
                      :once []}
           :client-opts tu/redis-client-opts
           :worker-opts tu/redis-worker-opts}
   :RabbitMQ {:fixtures {:each [tu/rmq-fixture]
                         :once []}
              :client-opts tu/rmq-client-opts
              :worker-opts tu/rmq-worker-opts}})


(defn broker-testable?
  "predicate on whether the broker implementation
  is capable enough to execute test-type"
  [broker test-type]
  (s/subset? (get test-reqs test-type)
             (c/fetch-capabilities broker)))

(comment
  (broker-testable? "Redis" :async-execution-test))
