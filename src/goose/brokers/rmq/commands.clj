(ns goose.brokers.rmq.commands
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [taoensso.nippy :as nippy]
    [langohr.basic :as lb]
    [langohr.queue :as lq]))

(defn create-queue
  [ch name]
  (memoize
    ; PRECONDITION_FAILED exception will be thrown when
    ; queue named 'prefixed-queue' exists with different attributes.
    (lq/declare ch name {:durable true :auto-delete false :exclusive false})))

(defn enqueue-back
  [ch job]
  (let [prefixed-queue (:prefixed-queue job)]
    (create-queue ch prefixed-queue)
    (lb/publish ch d/rmq-exchange prefixed-queue
                (nippy/freeze job)
                {:priority     0
                 :persistent   true
                 :mandatory    true
                 :content-type "ptaoussanis/nippy"})))
