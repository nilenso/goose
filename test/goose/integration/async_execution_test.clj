(ns goose.integration.async-execution-test
  (:require
   [goose.integration.test-utils :as tu]
   [goose.client :as c]
   [goose.worker :as w]
   [clojure.string :as s]
   [clojure.test :refer [deftest is testing report]]))

(def requirements #{})

(def perform-async-fn-executed (atom nil))

(defn perform-async-fn [arg]
  (reset! perform-async-fn-executed arg))

(deftest  async-execution-test
  (doseq [broker (keys tu/broker-utils)]
    (alter-meta! #'async-execution-test assoc :name (str (symbol broker) "-async-execution-test"))
    (reset! perform-async-fn-executed nil)
    (if (tu/broker-testable? broker requirements)
      (tu/with-fixtures broker
        (fn [ex] (report :default (ex-message ex)))
        (testing (str "Async Execution: " broker) 
          (let [_ (c/perform-async (tu/get-opts broker :client)
                                   `perform-async-fn
                                   ::async-execution-test)
                worker (w/start (tu/get-opts broker :worker))]
            (Thread/sleep 100)
            (is (= ::async-execution-test @perform-async-fn-executed))
            (w/stop worker))))
      (report :default (str "Async execution: " broker " is not testable")))))

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
