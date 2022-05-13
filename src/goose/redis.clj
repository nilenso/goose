(ns goose.redis
  (:require
    [taoensso.carmine :as car :refer (wcar)]))

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))
