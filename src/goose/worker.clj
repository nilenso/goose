(ns goose.worker
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.string :as string]))

(defn worker
  [queue]
  (let [job (r/wcar* (car/lpop queue))
        ns (first (string/split (first job) #"/"))]
    (require (symbol ns))
    (apply (-> (first job)
               (symbol)
               (resolve))
           (#'clojure.core/spread (rest job)))))


;(def job (r/wcar* (car/lpop "goose/queue:default")))
(comment (worker "goose/queue:default"))
;(worker "goose/queue:default-2")

;