(ns goose.integration.async-execution-test
  (:require
   [goose.integration.test-utils :as tu]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.string :as s]
   [clojure.test :refer [deftest is testing]]))


;; test reqs here itself as meta data
(deftest  async-execution-test
  (doseq [broker (keys tu/broker-utils)]
    (alter-meta! #'async-execution-test assoc :name (str broker "-async-execution-test"))
    ;; alter meta helps out with reporting for different brokers
    ;; fixtures needs to be manually handled -> clojure.test semantics too broad for the custom
    ;; behaviour we need here
    (is false)))

(comment 
  (defmacro gen-async-execution-test [broker]
    `(do
       (comment
         (tu/setup-test-environment ~broker "async-execution-test"))
       ;; TODO : don't switch nses
       ;; manual fixtures might be it
       (let [perform-async-fn-executed (atom (promise))
             perform-async-fn (fn [arg]
                                (deliver @perform-async-fn-executed arg))]
         (deftest ~(symbol (str "perform-" broker "-async-execution-test"))
           (testing (str "Goose [" broker "] executes a function asynchronously")
             (let [~'arg ::async-execute-test
                   _ (c/perform-async (->> broker
                                           (keyword)
                                           (get tu/broker-utils)
                                           (:client-opts))
                                      ~`perform-async-fn
                                      ~'arg)
                   worker (w/start (->> broker
                                        (keyword)
                                        (get tu/broker-utils)
                                        (:worker-opts)))]
               (is (= arg (deref @perform-async-fn-executed 100 :e2e-test-timed-out)))
               (w/stop-worker)))))))

  (tu/gen-test-suite  "async-execution-test" gen-async-execution-test))
