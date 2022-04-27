(ns goose.client
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]))

; QQQ: Maintain state that SADDs to "goose:queues" set only ONCE?
(defn async
  "Enqueues a function for asynchronous execution from an independent worker."
  [queue f & args]
  (r/wcar* (car/rpush queue [(str f) args]))
  )