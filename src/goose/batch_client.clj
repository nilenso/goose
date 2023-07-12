;; Ignore this file, this is for testing purpose and will be deleted once the PR is ready
(ns goose.batch-client
  (:require [goose.brokers.redis.broker :as redis]
            [goose.brokers.redis.commands :as redis-cmds]
            [goose.client :as client]
            [goose.defaults :as d]
            [goose.retry :as retry]
            [goose.worker :as w]
            [taoensso.carmine :as car]))
(def redis-producer (redis/new-producer redis/default-opts))
(def client-opts
  (assoc client/default-opts :broker redis-producer
                             :retry-opts (assoc retry/default-opts :max-retries 2)))

(def redis-conn (get-in client-opts [:broker :redis-conn]))

(defn my-fn
  "demo fn to pass to goose client"
  [arg1 arg2]
  (println "my-fn called with args" arg1 arg2))

(defn batch-callback
  "callback functions"
  []
  (println "Callback function is this"))

(defn wait-for-sometime
  [sec]
  (println "Started wait-for-sometime for " sec " secs")
  (Thread/sleep (* sec 1000))
  (println "Ended wait-for-sometime for " sec " secs")
  (* sec 1000))

(defn wait-and-divide-by-20
  [num]
  (Thread/sleep 20000)
  (/ 20 num))
(comment (wait-for-sometime 2))

; Batching
(comment (client/perform-batch client-opts
                               {}
                               `wait-for-sometime [[14] [35]]))

; Call the producer
(comment (client/perform-batch client-opts
                               {}
                               `wait-and-divide-by-20 [[2] [3] [0] [2]]))
(comment (client/perform-async client-opts `println 5 20))

(comment (client/perform-async client-opts
                               `wait-for-sometime 5))


;Callback Experimentation
(defn func []
  (car/multi)
  (car/set "mykey" "Hello")
  (car/get "mykey"))

(defn batch-complete?
  [enqueued-set-size retrying-set-size]
  (== (+ enqueued-set-size retrying-set-size) 0))

(defn move-and-check
  "Move job-id between 2 sets and check if batch is complete"
  [redis-conn src dst batch-id id]
  (let [[_ [_moved?
            enqueued-set-size
            retrying-set-size]] (redis-cmds/atomic
                                 redis-conn (fn []
                                              (car/multi)
                                              (car/smove src dst id)
                                              (car/scard (d/construct-batch-job-set
                                                          batch-id d/enqueued-job-set))
                                              (car/scard (d/construct-batch-job-set
                                                          batch-id d/retrying-job-set))))
        completed? (batch-complete? enqueued-set-size retrying-set-size)]
    completed?))

;; [["OK" "QUEUED" "QUEUED" "QUEUED"] [1 0 0]]

(comment (move-and-check redis-conn
                         (d/construct-batch-job-set "9e24a5c0-33ec-41cd-8f65-b03590909984" d/successful-job-set)
                         (d/construct-batch-job-set "9e24a5c0-33ec-41cd-8f65-b03590909984" d/dead-job-set)
                         "9e24a5c0-33ec-41cd-8f65-b03590909984"
                         "ea8b4b59-90a3-432c-acb0-58e4f8921595"))

(comment (defn exec
           [redis-conn id batch-id]
           (redis-cmds/run-with-txn redis-conn
                           (fn [] (car/multi)
                             (car/smove (d/construct-batch-job-set batch-id d/successful-job-set)
                                        (d/construct-batch-job-set batch-id "something") id)
                             (car/scard (d/construct-batch-job-set batch-id d/enqueued-job-set))
                             (car/scard (d/construct-batch-job-set batch-id d/retrying-job-set))))))



; Call the consumer
(def redis-consumer (redis/new-consumer redis/default-opts))
(comment (def worker (w/start worker-opts)))
(def worker-opts (assoc w/default-opts :broker redis-consumer))
(comment (def shutdown (w/stop worker)))
