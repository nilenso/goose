(ns goose.client)

; QQQ: Maintain state that SADDs to "goose:queues" set only ONCE?
; Speak with redis
; RPUSH to goose:queue/default
(defn async
  "Enqueues a function for asynchronous execution from
  an independent worker."
  [f]
  (println f)
  "random id")

(async #(println "hello from the past" 1 '(2 3 4) {:foo "bar"} #{:a :b}))

