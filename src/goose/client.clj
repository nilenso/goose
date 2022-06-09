(ns goose.client
  (:require
    [goose.broker :as broker]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.client :as v]))

(def default-opts
  {:broker-opts broker/default-opts
   :queue       d/default-queue})

(defn- enqueue
  [{:keys [broker-opts
           queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [enhanced-retry-opts (retry/enhance-opts retry-opts)]
    (v/validate-enqueue-params
      broker-opts queue
      enhanced-retry-opts
      execute-fn-sym args)
    (let [redis-conn (r/conn broker-opts)
          prefixed-queue (u/prefix-queue queue)
          job (j/new execute-fn-sym args prefixed-queue enhanced-retry-opts)]

      (if schedule
        (scheduler/run-at redis-conn schedule job)
        (j/enqueue redis-conn job))
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
