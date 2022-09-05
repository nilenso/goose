(ns goose.brokers.rmq.commands
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.job :as job]

    [langohr.basic :as lb]
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

(defn- enqueue
  [ch ex queue job {:keys [priority headers]}]
  (create-queue-and-exchanges ch queue)
  (lb/publish ch ex queue
              (nippy/freeze job)
              {:priority     priority
               :persistent   true
               :mandatory    true
               :content-type "ptaoussanis/nippy"
               :headers      headers}))

(defn enqueue-back
  ([ch {:keys [prefixed-queue] :as job}]
   (enqueue-back ch prefixed-queue job))
  ([ch queue job]
   (enqueue ch d/rmq-exchange queue job {:priority d/rmq-low-priority})))

(defn enqueue-front
  [ch {:keys [prefixed-queue] :as job}]
  (enqueue ch d/rmq-exchange prefixed-queue job {:priority d/rmq-high-priority}))

(defn schedule
  [ch job delay]
  (let [queue (job/execution-queue job)
        msg-properties {:priority d/rmq-high-priority
                        :headers  {"x-delay" delay}}]
    (when (< d/rmq-delay-limit-ms delay)
      (throw (ex-info "MAX_DELAY limit breached: 2^32 ms(~49 days 17 hours)" {:job job :delay delay})))
    (enqueue ch d/rmq-delay-exchange queue job msg-properties)))
