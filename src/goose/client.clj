(ns goose.client
  (:require
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.validations.client :refer [validate-async-params]]))

(def default-opts
  {:redis-url       d/default-redis-url
   :redis-pool-opts {}
   :queue           d/default-queue
   :schedule-opts   scheduler/default-opts
   :retry-opts      retry/default-opts})

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  Usage:
  - (async `foo)
  - (async `foo {:args '(:bar) :retries 2})
  Validations:
  - A function must be a resolvable & a fully qualified symbol
  - Args must be edn-serializable
  edn: https://github.com/edn-format/edn"
  [{:keys [redis-url redis-pool-opts
           queue schedule-opts retry-opts]
    :as   opts}
   execute-fn-sym
   & args]
  (validate-async-params
    redis-url redis-pool-opts
    queue schedule-opts retry-opts
    execute-fn-sym args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        job (j/new opts execute-fn-sym args)]
    (j/push redis-conn job)))
