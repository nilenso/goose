(ns ^:no-doc goose.brokers.rmq.commands
  (:require
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]
    [langohr.basic :as lb]
    [langohr.confirm :as lcnf])
  (:import
    (java.io IOException)))

(defn- publish
  [ch exch queue job {:keys [mandatory priority headers]}]
  (lb/publish ch exch queue
              (u/encode job)
              {:mandatory    mandatory
               ;; Priority isn't supported by quorum queues.
               :priority     priority
               :persistent   true
               :content-type d/content-type
               :headers      headers}))

(defn- async-enqueue
  [ch exch queue job properties]
  ;; ASYNC-enqueue is a 2-step process:
  ;; 1. Get next sequence number.
  ;; 2. Publish a message to queue.
  ;; Multiple threads might be using the same channel.
  ;; Acquire lock on a channel to avoid race conditions.
  (locking ch
    (let [seq (.getNextPublishSeqNo ch)]
      (publish ch exch queue job properties)
      {:delivery-tag seq :id (:id job)})))

(defn- sync-enqueue
  [ch
   exch
   queue
   {:keys [timeout-ms max-retries retry-delay-ms]
    :or   {max-retries 0 retry-delay-ms 10}}
   job
   properties]
  (u/with-retry {:count max-retries :retry-delay-ms retry-delay-ms}
    (publish ch exch queue job properties)
    ;; (wait-for-confirms-or-die) closes a channel on Negative-ACK.
    ;; Since we're maintaining a pool of channels,
    ;; throw an exception if wait-for-confirms returns false.
    (when-not (lcnf/wait-for-confirms ch timeout-ms)
      (throw (IOException. "rmq nack'd a msg"))))

  (select-keys job [:id]))

(defn- enqueue
  [ch
   exch
   {:keys [queue] :as queue-opts}
   {:keys [strategy] :as publisher-confirms}
   job
   properties]
  (rmq-queue/declare ch queue-opts)
  (condp = strategy
    d/sync-confirms
    (sync-enqueue ch exch queue publisher-confirms job properties)

    d/async-confirms
    (async-enqueue ch exch queue job properties)))

(defn enqueue-back
  ([ch queue-opts publisher-confirms job]
   (enqueue ch
            d/rmq-exchange queue-opts
            publisher-confirms
            job
            {:mandatory true
             :priority  d/rmq-low-priority})))

(defn enqueue-front
  [ch queue-opts publisher-confirms job]
  (enqueue ch
           d/rmq-exchange
           queue-opts
           publisher-confirms
           job
           {:mandatory true
            :priority  d/rmq-high-priority}))

(defn schedule
  [ch queue-opts publisher-confirms job delay-ms]
  (when (< d/rmq-delay-limit-ms delay-ms)
    (throw (ex-info "MAX_DELAY limit breached: 2^32 ms(~49 days 17 hours)" {:job job :delay delay-ms})))
  (enqueue ch
           d/rmq-delay-exchange
           queue-opts
           publisher-confirms
           job
           ;; delayed-message-plugin doesn't support mandatory flag.
           ;; https://github.com/rabbitmq/discussions/issues/106#issuecomment-635931866
           {:mandatory false
            :priority  d/rmq-high-priority
            :headers   {"x-delay" delay-ms}}))

(defn replay-dead-job
  [ch queue-type publisher-confirms]
  (let [[{:keys [delivery-tag]} payload] (lb/get ch d/prefixed-dead-queue false)]
    (when (some? payload)
      (let [job (u/decode payload)]
        (try
          (enqueue-front ch
                         (assoc queue-type :queue (job/ready-queue job))
                         publisher-confirms
                         job)
          (lb/ack ch delivery-tag)
          true
          (catch Exception e
            (log/error e "Error enqueuing dead-job to ready queue.")
            (lb/reject ch delivery-tag true)
            (throw e)))))))
