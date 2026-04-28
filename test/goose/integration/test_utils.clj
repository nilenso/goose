(ns goose.integration.test-utils
  (:require
   [goose.test-utils :as tu]
   [goose.capability :as c]
   [clojure.set :as s]
   [goose.specs :as specs]
   [goose.brokers.rmq.queue :as rmq-queue]
   [clojure.test :refer [testing report deftest]]))

(def broker-utils
  {:commons {:execution-timeout-ms 3000}
   :implementations {:redis {:fixture (fn [test-fn failure-reporter]
                                        (specs/instrument)
                                        (tu/clear-redis)
                                        (try
                                          (test-fn)
                                          (catch Exception ex
                                            (failure-reporter ex)
                                            (throw ex))
                                          (finally (tu/clear-redis))))
                             :opts {:client tu/redis-client-opts
                                    :worker tu/redis-worker-opts}}
                     :rabbitmq {:fixture (fn [test-fn failure-reporter]
                                           (specs/instrument)
                                           (tu/rmq-delete-test-queues)
                                           (rmq-queue/clear-cache)
                                           (try
                                             (test-fn)
                                             (catch Exception ex
                                               (failure-reporter ex)
                                               (throw ex))
                                             (finally (tu/rmq-delete-test-queues))))
                                :opts {:client tu/rmq-client-opts
                                       :worker tu/rmq-worker-opts}}}})

(defn get-configs [& args]
  (get-in broker-utils args))

(defn get-opts [broker opts-type]
  (get-configs :implementations broker :opts opts-type))

(def executed-log (atom {}))

(defn setup-test-promise [test-name]
  (swap! executed-log
         assoc test-name (promise)))

(defn executable [test-name executed-flag]
  (when-let [p (get @executed-log test-name)]
    (deliver p executed-flag)))

(defn delivered-execution [test-name]
  (deref (get @executed-log test-name)
         (get-configs :commons :execution-timeout-ms)
         ::timed-out))

(defn broker-testable? [broker requirements]
  (s/subset? requirements
             (c/fetch-capabilities broker)))

(defmacro with-fixture [broker failure-reporter & body]
  `(let [fixture-fn# (get-configs :implementations ~broker :fixture)]
     (fixture-fn#
      (fn [] ~@body)
      ~failure-reporter)))

(defmacro def-integration-test [base-test-name requirements & body]
  `(let [implementations# (keys (get-configs :implementations))]
     (deftest ~base-test-name
       (doseq [~'broker implementations#]
         (let [~'test-name (str (symbol ~'broker) "-" ~base-test-name)]
           (if (broker-testable? ~'broker ~requirements)
             (do
               (setup-test-promise ~'test-name)
               (with-fixture ~'broker
                 (fn [ex#] (report {:type :default :message (ex-message ex#)}))
                 (testing (str ~base-test-name " [" ~'broker "]")
                   ~@body)))
             (report {:type :default
                      :message (str ~base-test-name " [" ~'broker " is not testable]")})))))))
