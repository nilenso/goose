(ns goose.working-code
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]))

(defn variable-args-fn
  [& args]
  (println (type (nth args 0)))
  (println (type (nth args 1)))
  (println (type (nth args 2)))
  (println (type (nth args 3)))
  (println (type (nth args 4)))
  (println (type (nth args 5))))

(defn fixed-args-fn
  [a b c]
  (+ a b c))

(defn async
  "Enqueues a function for asynchronous execution from an independent worker."
  [queue f & args]
  ; Serialize fn, namespace & args and store it in Redis
  (r/wcar* (car/rpush queue [(str f) args])))

(defn worker
  [queue]
  (let [job (r/wcar* (car/lpop queue))]
    (apply (-> (first job)
               (symbol)
               (resolve))
           (#'clojure.core/spread (rest job)))))

(let [queue "goose/queue:default"]
  (async queue
         'goose.working-code/variable-args-fn
         1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2)
  (worker queue))

(let [queue "goose/queue:default-2"]
  (async queue
         'goose.working-code/fixed-args-fn
         1 2 3)
  (worker queue))
