(ns goose.worker
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]))

(defn worker
  [queue]
  (let [job (r/wcar* (car/lpop queue))]
    (apply (-> (first job)
               (symbol)
               (resolve))
           (#'clojure.core/spread (rest job)))))
