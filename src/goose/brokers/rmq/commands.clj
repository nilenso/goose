(ns goose.brokers.rmq.commands
  {:no-doc true}
  (:require
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.basic :as lb]
    [langohr.confirm :as lcnf]
    [langohr.exchange :as lex]
    [langohr.queue :as lq])
  (:import
    (java.io IOException)))

(defn create-queue-and-exchanges
  [ch {:keys [queue] :as queue-opts}]
  (lex/declare ch
               d/rmq-delay-exchange
               d/rmq-delay-exchange-type
               {:durable     true
                :auto-delete false
                :arguments   {"x-delayed-type" "direct"}})

  (let [arguments (rmq-queue/arguments queue-opts)]
    (lq/declare ch
                queue
                {:durable     true
                 :auto-delete false
                 :exclusive   false
                 :arguments   arguments}))
  (lq/bind ch queue d/rmq-delay-exchange {:routing-key queue}))

(defn- publish
  [ch exch queue job {:keys [priority headers]}]
  (lb/publish ch exch queue
              (u/encode job)
              {:priority     priority
               :persistent   true
               :mandatory    true
               :content-type d/content-type
               :headers      headers}))

(defn- async-enqueue
  [ch exch queue job properties]
  ; ASYNC-enqueue is a 2-step process:
  ; 1. Get next sequence number.
  ; 2. Publish a message to queue.
  ; Multiple threads might be using the same channel.
  ; Acquire lock on a channel to avoid race conditions.
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
    ; (wait-for-confirms-or-die) closes a channel on Negative-ACK.
    ; Since we're maintaining a pool of channels,
    ; throw an exception if wait-for-confirms returns false.
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
  (create-queue-and-exchanges ch queue-opts)
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
            ; Priority isn't supported by quorum queues.
            {:priority d/rmq-low-priority})))

(defn enqueue-front
  [ch queue-opts publisher-confirms job]
  (enqueue ch
           d/rmq-exchange
           queue-opts
           publisher-confirms
           job
           ; Priority isn't supported by quorum queues.
           {:priority d/rmq-high-priority}))

(defn schedule
  [ch queue-opts publisher-confirms job delay-ms]
  (when (< d/rmq-delay-limit-ms delay-ms)
    (throw (ex-info "MAX_DELAY limit breached: 2^32 ms(~49 days 17 hours)" {:job job :delay delay-ms})))
  (enqueue ch
           d/rmq-delay-exchange
           queue-opts
           publisher-confirms
           job
           ; Priority isn't supported by quorum queues.
           {:priority d/rmq-high-priority
            :headers  {"x-delay" delay-ms}}))
