(ns goose.integration.test-utils
  (:require
   [goose.test-utils :as tu]
   [goose.capability :as c]
   [clojure.set :as s]
   [clojure.test :refer [deftest use-fixtures testing is]]
   [goose.specs :as specs]
   [goose.brokers.rmq.queue :as rmq-queue]))

(comment
  (def broker-utils
    {:test {:fixtures {:pre [#(println "calling pre 1")
                             #(println "calling pre 2")]
                       :post [#(println "calling post 1")
                              #(println "calling post 2")]}}}))

(def broker-utils
  {:redis {:fixtures {:pre [specs/instrument
                            tu/clear-redis]
                      :post [tu/clear-redis]}
           :client-opts tu/redis-client-opts
           :worker-opts tu/redis-worker-opts}
   :rabbitmq {:fixtures {:pre [specs/instrument
                               tu/rmq-delete-test-queues
                               rmq-queue/clear-cache]
                         :post [tu/rmq-delete-test-queues]}
              :client-opts tu/rmq-client-opts
              :worker-opts tu/rmq-worker-opts}})

(defn broker-testable?
  "predicate on whether the broker implementation
  is capable enough to execute test-type"
  [broker requirements]
  (s/subset? requirements 
             (c/fetch-capabilities broker)))

(defmacro with-fixtures [broker & body]
  (letfn [(fetch-fixtures [broker pos]
            (map list
                 (get-in broker-utils
                         [broker :fixtures pos])))]
    `(do 
       ~@(fetch-fixtures broker :pre)
       ~@body
       ~@(fetch-fixtures broker :post))))

(comment
  (with-fixtures :redis
    (run some test stuff)
    (run some more test stuff))

  )

(comment
  (setup-test-environment "redis" "async-execution-test"))

(comment 
  (defmacro gen-test-suite [test-type test-generator]
    `(do
       ~@(for [broker (keys broker-utils)]
           (when (broker-testable? broker test-type)
             (test-generator broker))))))

(comment
  (broker-testable? "redis" :async-execution-test))

(comment
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
    (register-fixtures "redis")

    )

  (defmacro  setup-test-environment [broker test-type]
    `(do (switch-ns ~broker ~test-type)
         (register-fixtures ~broker))))
