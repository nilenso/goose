(ns goose.integration.goose-test
  (:require
   [goose.integration.utils :as u :refer [executable
                                          delivered-execution
                                          def-integration-test]]
   [goose.client :as c]
   [goose.retry :as retry]
   [goose.worker :as w]
   [clojure.test :refer [is]])
  (:import
   [clojure.lang ExceptionInfo]
   [java.time Instant]
   [java.util UUID]))

(def-integration-test async-execution-test
  #{:enqueue}
  (let [job (c/perform-async (u/get-opts broker :client)
                             `executable
                             test-name
                             ::async-execution-test)
        worker (w/start (u/get-opts broker :worker))]
    (is (uuid? (UUID/fromString (:id job))))
    (is (= ::async-execution-test
           (delivered-execution test-name)))
    (w/stop worker)))

(def-integration-test absolute-scheduling-test
  #{:schedule}
  (let [job (c/perform-at (u/get-opts broker :client)
                          (Instant/now)
                          `executable
                          test-name
                          ::absolute-scheduling-test)
        scheduler (w/start (u/get-opts broker :worker))]
    (is (uuid? (UUID/fromString (:id job))))
    (is (= ::absolute-scheduling-test
           (delivered-execution test-name)))
    (w/stop scheduler)))

(def-integration-test relative-scheduling-test
  #{:schedule}
  (let [job (c/perform-in-sec (u/get-opts broker :client)
                              1
                              `executable
                              test-name
                              ::relative-scheduling-test)
        scheduler (w/start (u/get-opts broker :worker))]
    (is (uuid? (UUID/fromString (:id job))))
    (is (= ::relative-scheduling-test
           (delivered-execution test-name)))
    (w/stop scheduler)))

(defn add-five [arg]
  (+ 5 arg))

(defn test-middleware [test-name]
  (fn [next]
    (fn [opts job]
      (let [result (next opts job)]
        (executable test-name result)))))

(def-integration-test middleware-test
  #{:enqueue}
  (let [worker (w/start (assoc (u/get-opts broker :worker)
                               :middlewares (test-middleware test-name)))]
    (try
      (c/perform-async (u/get-opts broker :client) `add-five 5)
      (is (= 10 (delivered-execution test-name)))
      (finally
        (w/stop worker)))))

(def retry-queue "test-retry")

(defn immediate-retry [_]
  1)

(defn delivery-name [test-name event]
  (str test-name "-" (name event)))

(defn setup-deliveries [test-name events]
  (doseq [event events]
    (u/setup-test-promise (delivery-name test-name event))))

(defn retry-test-error-handler [config {:keys [args]} ex]
  (let [test-name (first args)
        failed-on-execute (delivery-name test-name :failed-on-execute)]
    (executable (delivery-name test-name :retry-error-service) config)
    (if (u/delivered? failed-on-execute)
      (executable (delivery-name test-name :failed-on-1st-retry) ex)
      (executable failed-on-execute ex))))

(defn erroneous-fn [test-name arg]
  (when-not (u/delivered? (delivery-name test-name :failed-on-execute))
    (/ 1 0))
  (when-not (u/delivered? (delivery-name test-name :failed-on-1st-retry))
    (throw (ex-info "error" {})))
  (executable (delivery-name test-name :succeeded-on-2nd-retry) arg))

(def-integration-test retry-test
  #{:enqueue :schedule}
  (setup-deliveries test-name [:failed-on-execute
                               :failed-on-1st-retry
                               :succeeded-on-2nd-retry
                               :retry-error-service])
  (let [arg "retry-test"
        retry-opts (assoc retry/default-opts
                          :max-retries 2
                          :retry-delay-sec-fn-sym `immediate-retry
                          :retry-queue retry-queue
                          :error-handler-fn-sym `retry-test-error-handler)
        error-svc-cfg :my-retry-test-config
        worker-opts (assoc (u/get-opts broker :worker)
                           :error-service-config error-svc-cfg)
        worker (w/start worker-opts)
        retry-worker (w/start (assoc worker-opts :queue retry-queue))]
    (try
      (c/perform-async (assoc (u/get-opts broker :client)
                              :retry-opts retry-opts)
                       `erroneous-fn
                       test-name
                       arg)
      (is (= ArithmeticException
             (type (delivered-execution
                    (delivery-name test-name :failed-on-execute)))))
      (is (= error-svc-cfg
             (delivered-execution
              (delivery-name test-name :retry-error-service))))
      (is (= ExceptionInfo
             (type (delivered-execution
                    (delivery-name test-name :failed-on-1st-retry)))))
      (is (= arg
             (delivered-execution
              (delivery-name test-name :succeeded-on-2nd-retry))))
      (finally
        (w/stop worker)
        (w/stop retry-worker)))))

(def dead-job-run-count (atom 0))

(defn dead-test-error-handler [_ _ _])

(defn dead-test-death-handler [config {:keys [args]} ex]
  (let [test-name (first args)]
    (executable (delivery-name test-name :death-error-service) config)
    (executable test-name ex)))

(defn dead-fn [& _]
  (swap! dead-job-run-count inc)
  (/ 1 0))

(def-integration-test death-test
  #{:enqueue :schedule}
  (setup-deliveries test-name [:death-error-service])
  (reset! dead-job-run-count 0)
  (let [dead-job-opts (assoc retry/default-opts
                             :max-retries 1
                             :retry-delay-sec-fn-sym `immediate-retry
                             :error-handler-fn-sym `dead-test-error-handler
                             :death-handler-fn-sym `dead-test-death-handler)
        error-svc-cfg :my-death-test-config
        worker-opts (assoc (u/get-opts broker :worker)
                           :error-service-config error-svc-cfg)
        worker (w/start worker-opts)]
    (try
      (c/perform-async (assoc (u/get-opts broker :client)
                              :retry-opts dead-job-opts)
                       `dead-fn
                       test-name
                       :foo)
      (is (= ArithmeticException
             (type (delivered-execution test-name))))
      (is (= error-svc-cfg
             (delivered-execution
              (delivery-name test-name :death-error-service))))
      (is (= 2 @dead-job-run-count))
      (finally
        (w/stop worker)))))
