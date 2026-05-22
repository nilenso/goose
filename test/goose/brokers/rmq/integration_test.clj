(ns goose.brokers.rmq.integration-test
  (:require
   [goose.api.enqueued-jobs :as enqueued-jobs]
   [goose.brokers.rmq.broker :as rmq]
   [goose.brokers.rmq.queue :as rmq-queue]
   [goose.client :as c]
   [goose.defaults :as d]
   [goose.retry :as retry]
   [goose.test-utils :as tu]
   [goose.worker :as w]

   [clojure.test :refer [deftest is testing use-fixtures]]
   [langohr.confirm :as lcnf])
  (:import
   [clojure.lang ExceptionInfo]
   [java.util UUID]
   (java.util.concurrent TimeoutException)))

;;; ======= Setup & Teardown ==========
(use-fixtures :each tu/rmq-fixture)

;;; ======= TEST: Async execution ACK ==========
(def async-ack-fn-executed (atom (promise)))
(defn async-ack-fn [arg]
  (deliver @async-ack-fn-executed arg))

(deftest async-execution-ack-test
  (testing "[rmq] Goose ACKs a message upon successful completion"
    (reset! async-ack-fn-executed (promise))
    (let [arg "async-ack-test"
          _ (c/perform-async tu/rmq-client-opts `async-ack-fn arg)
          worker (w/start tu/rmq-worker-opts)]
      (is (= arg (deref @async-ack-fn-executed 100 :async-ack-test-timed-out)))
      (w/stop worker)
      (is (zero? (enqueued-jobs/size tu/rmq-producer (:queue tu/rmq-client-opts)))))))

;;; ======= TEST: Async execution (quorum queue) ==========
(def quorum-fn-executed (atom (promise)))
(defn quorum-fn [arg]
  (deliver @quorum-fn-executed arg))

(deftest quorum-queue-test
  (testing "[rmq] Goose enqueues jobs to quorum queues"
    (reset! quorum-fn-executed (promise))

    (let [queue "quorum-test"
          arg "quorum-arg"
          opts (assoc tu/rmq-opts :queue-type rmq-queue/quorum)
          producer (rmq/new-producer opts)
          client-opts {:queue      queue
                       :retry-opts retry/default-opts
                       :broker     producer}

          consumer (rmq/new-consumer opts)
          worker-opts (assoc tu/worker-opts
                             :broker consumer
                             :queue queue)

          _ (is (uuid? (UUID/fromString (:id (c/perform-async client-opts `quorum-fn arg)))))
          worker (w/start worker-opts)]
      (is (= arg (deref @quorum-fn-executed 100 :quorum-test-timed-out)))
      (w/stop worker)
      (rmq/close producer)
      (rmq/close consumer))))

;;; ======= TEST: Scheduling MAX_DELAY limit =======
(deftest scheduling-max-delay-test
  (testing "[rmq] Scheduling beyond max_delay limit"
    (is
     (thrown-with-msg?
      ExceptionInfo
      #"MAX_DELAY limit breached*"
      (c/perform-in-sec tu/rmq-client-opts 4294968 `tu/my-fn)))))

;;; ======= TEST: Publisher Confirms =======
(def ack-channel-number (atom (promise)))
(def ack-delivery-tag (atom (promise)))
(defn test-ack-handler [ch-no tag _]
  (deliver @ack-channel-number ch-no)
  (deliver @ack-delivery-tag tag))
(defn test-nack-handler [_ _ _])
(deftest publisher-confirm-test
  (testing "[rmq][sync-confirms] Publish timed out"
    (let [opts (assoc tu/rmq-opts
                      :publisher-confirms {:strategy d/sync-confirms
                                           :timeout-ms 1000})
          producer (rmq/new-producer opts 1)
          client-opts {:queue      "sync-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     producer}]
      (try
        (with-redefs [lcnf/wait-for-confirms (fn [_ ^long _]
                                               (throw (TimeoutException.)))]
          (is
           (thrown?
            TimeoutException
            (c/perform-async client-opts `tu/my-fn))))
        (finally
          (rmq/close producer)))))

  (testing "[rmq][async-confirms] Ack handler called"
    (reset! ack-channel-number (promise))
    (reset! ack-delivery-tag (promise))
    (let [opts (assoc tu/rmq-opts
                      :publisher-confirms {:strategy     d/async-confirms
                                           :ack-handler  test-ack-handler
                                           :nack-handler test-nack-handler})
          ;; Create multiple channels to test correctness of delivery-tag & channel-number.
          producer (rmq/new-producer opts 5)
          client-opts {:queue      "async-publisher-confirms-test"
                       :retry-opts retry/default-opts
                       :broker     producer}
          enqueued-job (c/perform-in-sec client-opts 1 `tu/my-fn)]
      (is (= (:channel-number enqueued-job) (deref @ack-channel-number 100 :async-publisher-confirm-test-timed-out)))
      (is (= (:delivery-tag enqueued-job) (deref @ack-delivery-tag 1 :async-publisher-confirm-test-timed-out)))
      (rmq/close producer))))

;;; ======= TEST: Middleware RMQ Metadata ==========
(def middleware-called (atom (promise)))
(defn test-middleware
  [next]
  (fn [{:keys [metadata] :as opts} job]
    (deliver @middleware-called metadata)
    (next opts job)))

(deftest middleware-metadata-test
  (testing "[rmq] Goose attaches RMQ metadata to middleware opts"
    (reset! middleware-called (promise))
    (let [worker (w/start (assoc tu/rmq-worker-opts
                                 :middlewares test-middleware))]
      (try
        (c/perform-async tu/rmq-client-opts `tu/my-fn :arg1)
        (is (= d/content-type
               (:content-type (deref @middleware-called
                                     100
                                     :middleware-test-timed-out))))
        (finally
          (w/stop worker))))))

;;; ======= TEST: Graceful shutdown ==========
(def sleepy-fn-called (atom (promise)))
(def sleepy-fn-completed (atom (promise)))
(defn sleepy-fn
  [arg]
  (deliver @sleepy-fn-called arg)
  (Thread/sleep 2000)
  (deliver @sleepy-fn-completed arg))

(deftest graceful-shutdown-test
  (testing "[rmq] Goose shuts down a worker gracefully"
    (reset! sleepy-fn-called (promise))
    (reset! sleepy-fn-completed (promise))
    (let [arg "graceful-shutdown-test"
          _ (c/perform-async tu/rmq-client-opts `sleepy-fn arg)
          worker (w/start (assoc tu/rmq-worker-opts :graceful-shutdown-sec 2))]
      (is (= arg (deref @sleepy-fn-called 100 :graceful-shutdown-test-timed-out)))
      (w/stop worker)
      (is (= arg (deref @sleepy-fn-completed 100 :non-graceful-shutdown))))))

