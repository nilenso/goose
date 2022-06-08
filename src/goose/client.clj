(ns goose.client
  (:require
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.client :as validate]))

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}
   :queue           d/default-queue})

(defn- enqueue
  [{:keys [redis-url redis-pool-opts
           queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [enhanced-retry-opts (retry/enhance-opts retry-opts)]
    (validate/enqueue-params
      redis-url redis-pool-opts
      queue enhanced-retry-opts
      execute-fn-sym args)
    (let [redis-conn (r/conn redis-url redis-pool-opts)
          prefixed-queue (u/prefix-queue queue)
          job (j/new execute-fn-sym args prefixed-queue enhanced-retry-opts)]

      (if schedule
        (scheduler/schedule-job redis-conn schedule job)
        (j/enqueue redis-conn job))
      (:id job))))

(defn perform-async
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn perform-at
  [opts date-time execute-fn-sym & args]
  (validate/date-time date-time)
  (enqueue opts (u/epoch-time-ms date-time) execute-fn-sym args))

(defn perform-in-sec
  [opts sec execute-fn-sym & args]
  (validate/seconds sec)
  (enqueue opts (u/add-sec sec) execute-fn-sym args))
