(ns goose.client
  (:require
    [goose.brokers.broker :as broker]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.client :as v]))

(defonce default-opts
         {:queue      d/default-queue
          :retry-opts retry/default-opts})

(defn- enqueue
  [{:keys [broker-opts
           queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        broker-opts (broker/create broker-opts)]
    (println retry-opts)
    (v/validate-enqueue-params
      broker-opts queue
      retry-opts
      execute-fn-sym args)

    (let [redis-conn (r/conn broker-opts)
          prefixed-queue (d/prefix-queue queue)
          job (j/new execute-fn-sym args prefixed-queue retry-opts)]

      (if schedule
        (scheduler/run-at redis-conn schedule job)
        (r/enqueue-back redis-conn prefixed-queue job))
      (:id job))))

(defn perform-async
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn perform-at
  [opts date-time execute-fn-sym & args]
  (v/validate-perform-at-params date-time)
  (enqueue opts (u/epoch-time-ms date-time) execute-fn-sym args))

(defn perform-in-sec
  [opts sec execute-fn-sym & args]
  (v/validate-perform-in-sec-params sec)
  (enqueue opts (u/add-sec sec) execute-fn-sym args))
