(ns goose.client
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.client :as redis-client]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]))

(def default-opts
  "Default config for Goose client."
  {:broker-opts redis-client/default-opts
   :queue       d/default-queue
   :retry-opts  retry/default-opts})

(defn- enqueue
  [{:keys [broker-opts
           queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [redis-conn (broker/new broker-opts)
        retry-opts (retry/prefix-queue-if-present retry-opts)
        prefixed-queue (d/prefix-queue queue)
        job (j/new execute-fn-sym args queue prefixed-queue retry-opts)]

    (if schedule
      (scheduler/run-at redis-conn schedule job)
      (redis-cmds/enqueue-back redis-conn prefixed-queue job))
    (:id job)))

(defn perform-async
  "Enqueue a function for async execution.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic. "
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn perform-at
  "Schedule a function for execution at given date & time.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic. "
  [opts date-time execute-fn-sym & args]
  (enqueue opts (u/epoch-time-ms date-time) execute-fn-sym args))

(defn perform-in-sec
  "Schedule a function for execution in given seconds.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic. "
  [opts sec execute-fn-sym & args]
  (enqueue opts (u/add-sec sec) execute-fn-sym args))
