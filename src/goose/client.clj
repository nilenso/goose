(ns goose.client
  (:require
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.client :refer [validate-async-params]]))

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}
   :queue           d/default-queue
   :retry-opts      retry/default-opts})

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  Usage:
  - (async `foo)
  Validations:
  - A function must be a resolvable & a fully qualified symbol
  - Args must be edn-serializable
  edn: https://github.com/edn-format/edn"
  [{:keys [redis-url redis-pool-opts
           queue schedule retry-opts]}
   execute-fn-sym
   & args]
  (validate-async-params
    redis-url redis-pool-opts
    queue schedule retry-opts
    execute-fn-sym args)

  (let [redis-conn (r/conn redis-url redis-pool-opts)
        prefixed-queue (u/prefix-queue queue)
        job (j/new execute-fn-sym args prefixed-queue retry-opts)]
    (if schedule
      (scheduler/schedule-job redis-conn schedule job)
      (j/enqueue redis-conn job))
    (:id job)))
