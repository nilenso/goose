(ns goose.integration.test-utils
  (:require
   [goose.test-utils :as tu]
   [goose.capability :as c]
   [clojure.set :as s]
   [clojure.test :refer [deftest use-fixtures testing is report]]
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
           :opts {:client tu/redis-client-opts
                  :worker tu/redis-worker-opts}}
   :rabbitmq {:fixtures {:pre [specs/instrument
                               tu/rmq-delete-test-queues
                               rmq-queue/clear-cache]
                         :post [tu/rmq-delete-test-queues]}
              :opts {:client tu/rmq-client-opts
                     :worker tu/rmq-worker-opts}}})

(defn get-opts [broker opts-type]
  (get-in broker-utils [broker :opts opts-type]))

(defn broker-testable?
  "predicate on whether the broker implementation
  is capable enough to execute test-type"
  [broker requirements]
  (s/subset? requirements 
             (c/fetch-capabilities broker)))

(defmacro with-fixtures [broker failure-reporter & body]
  (letfn [(fetch-fixtures [broker pos]
            (map list
                 (get-in broker-utils
                         [broker :fixtures pos])))]
    `(do 
       ~@(fetch-fixtures broker :pre)
       (try
         ~@body
         (catch Throwable ex#
           (~failure-reporter ex#)
           (throw ex#))
         (finally
           ~@(fetch-fixtures broker :post))))))

(comment
  (with-fixtures :redis
    (run some test stuff)
    (run some more test stuff))
  )
