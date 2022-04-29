(ns goose.server
  (:require
    [goose.client :as c]
    [goose.worker :as w]))

(defn variable-args-fn
  [& args]
  (println (nth args 0))
  (println (nth args 1))
  (println (type (nth args 2)))
  (println (type (nth args 3)))
  (println (type (nth args 4)))
  (println (type (nth args 5))))
;(variable-args-fn 1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2)

(defn fixed-args-fn
  [a b c]
  (+ a b c))
;(fixed-args-fn 1 2 3)

; This code won't work when worker is in a different namespace :(
(comment
  (c/async
    `variable-args-fn
    {:args '(1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2)
     :queue "abcd"})
  (w/worker "abcd")
  (c/async
    "goose/queue:default-2"
    'goose.server/fixed-args-fn
    1 2 3))