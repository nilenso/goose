(ns goose.worker
  (:require [taoensso.carmine :as car :refer (wcar)]))

(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379/"}}) ; See `wcar` docstring for opts
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
; connect to redis/rabbitmq.
; pull from queue.
; execute the function.
(wcar* (car/ping))