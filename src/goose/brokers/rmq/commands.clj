(ns goose.brokers.rmq.commands
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [taoensso.nippy :as nippy]
    [langohr.basic :as lb]
    [langohr.exchange :as lex]
    [langohr.queue :as lq]))

(def common-properties
  {:durable     true
   :auto-delete false
   :exclusive   false})


(defn- memoized-create-queue-and-exchange
  [ch prefixed-queue]
  (lex/declare ch
               d/prefixed-schedule-queue
               d/rmq-delay-exchange
               (assoc common-properties :arguments {"x-delayed-type" "direct"}))

  ; PRECONDITION_FAILED exception will be thrown when
  ; queue named 'prefixed-queue' exists with different attributes.
  (lq/declare ch
              prefixed-queue
              (assoc common-properties :arguments {"x-max-priority" d/rmq-high-priority}))
  (lq/bind ch prefixed-queue d/prefixed-schedule-queue {:routing-key prefixed-queue}))

(defn create-queue-and-exchanges
  [ch prefixed-queue]
  (memoize (memoized-create-queue-and-exchange ch prefixed-queue)))

(defn- enqueue
  [ch ex job {:keys [priority headers]}]
  (let [prefixed-queue (:prefixed-queue job)]
    (create-queue-and-exchanges ch prefixed-queue)
    (lb/publish ch ex prefixed-queue
                (nippy/freeze job)
                {:priority     priority
                 :persistent   true
                 :mandatory    true
                 :content-type "ptaoussanis/nippy"
                 :headers      headers})))

(defn enqueue-back
  [ch job]
  (enqueue ch d/rmq-exchange job {:priority d/rmq-low-priority}))

(defn enqueue-front
  [ch job]
  (enqueue ch d/rmq-exchange job {:priority d/rmq-high-priority}))

(defn schedule
  [ch job delay]
  (create-queue-and-exchanges ch (:prefixed-queue job))
  (enqueue
    ch
    d/prefixed-schedule-queue
    job
    {:priority d/rmq-high-priority
     :headers  {"x-delay" delay}}))
