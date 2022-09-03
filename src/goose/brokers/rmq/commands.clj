(ns goose.brokers.rmq.commands
  {:no-doc true}
  (:require
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]
    [goose.defaults :as d]
    [goose.job :as job]

    [langohr.basic :as lb]
    [langohr.confirm :as lcnf]
    [langohr.exchange :as lex]
    [langohr.queue :as lq]
    [taoensso.nippy :as nippy]))

(def common-properties
  {:durable     true
   :auto-delete false
   :exclusive   false})

(defn- memoized-create-queue-and-exchange
  [ch queue]
  (lex/declare ch
               d/rmq-delay-exchange
               d/rmq-delay-exchange-type
               (assoc common-properties :arguments {"x-delayed-type" "direct"}))

  (lq/declare ch
              queue
              (assoc common-properties :arguments {"x-max-priority" d/rmq-high-priority}))
  (lq/bind ch queue d/rmq-delay-exchange {:routing-key queue}))

(defn create-queue-and-exchanges
  [ch queue]
  (memoize (memoized-create-queue-and-exchange ch queue)))

(defn- publish
  [ch ex queue job {:keys [priority headers]}]
  (lb/publish ch ex queue
              (nippy/freeze job)
              {:priority     priority
               :persistent   true
               :mandatory    true
               :content-type "ptaoussanis/nippy"
               :headers      headers}))

(defn- async-enqueue
  [ch ex queue job msg-properties]
  ; ASYNC-enqueue is a 2-step process.
  ; Step 1: Get next sequence number.
  ; Step 2: Publish a message to queue.
  ; Multiple threads might be using the same channel.
  ; Acquire lock on a channle to avoid race conditions.
  (locking ch
    (let [seq (.getNextPublishSeqNo ch)]
      (publish ch ex queue job msg-properties)
      {:delivery-tag seq :id (:id job)})))

(defn- sync-enqueue
  [ch ex queue job msg-properties timeout]
  (publish ch ex queue job msg-properties)
  (lcnf/wait-for-confirms ch timeout)
  {:id (:id job)})

(defn- enqueue
  [ch {:keys [strategy timeout]} ex queue job msg-properties]
  (create-queue-and-exchanges ch queue)
  (condp = strategy
    rmq-publisher-confirms/sync
    (sync-enqueue ch ex queue job msg-properties timeout)

    rmq-publisher-confirms/async
    (async-enqueue ch ex queue job msg-properties)))

(defn enqueue-back
  ([ch publisher-confirms job]
   (enqueue-back ch publisher-confirms job (job/execution-queue job)))
  ([ch publisher-confirms job queue]
   (enqueue
     ch
     publisher-confirms
     d/rmq-exchange
     queue
     job
     {:priority d/rmq-low-priority})))

(defn enqueue-front
  [ch publisher-confirms job]
  (enqueue
    ch
    publisher-confirms
    d/rmq-exchange
    (job/execution-queue job)
    job
    {:priority d/rmq-high-priority}))

(defn schedule
  [ch publisher-confirms job delay]
  (let [msg-properties {:priority d/rmq-high-priority
                        :headers  {"x-delay" delay}}]
    (when (< d/rmq-delay-limit-ms delay)
      (throw (ex-info "MAX_DELAY limit breached: 2^32 ms(~49 days 17 hours)" {:job job :delay delay})))
    (enqueue
      ch
      publisher-confirms
      d/rmq-delay-exchange
      (job/execution-queue job)
      job
      msg-properties)))
