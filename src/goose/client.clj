(ns goose.client
  (:require
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.retry :as retry]
    [goose.utils :as u])
  (:import
    (java.time Instant)))

(def default-opts
  "Default config for Goose client."
  {:queue      d/default-queue
   :retry-opts retry/default-opts})

(defn- register-cron-schedule
  [{:keys [broker queue retry-opts] :as _opts}
   cron-opts
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        ready-queue (d/prefix-queue queue)
        cron-entry (b/register-cron broker
                                    cron-opts
                                    (j/description execute-fn-sym
                                                   args
                                                   queue
                                                   ready-queue
                                                   retry-opts))]
    (select-keys cron-entry [:cron-name :cron-schedule :timezone])))

(defn- enqueue
  [{:keys [broker queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        ready-queue (d/prefix-queue queue)
        job (j/new execute-fn-sym args queue ready-queue retry-opts)]

    (if schedule
      (b/schedule broker schedule job)
      (b/enqueue broker job))))

(defn perform-async
  "Enqueue a function for async execution.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic."
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn perform-at
  "Schedule a function for execution at given date & time.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic."
  [opts ^Instant instant execute-fn-sym & args]
  (enqueue opts (u/epoch-time-ms instant) execute-fn-sym args))

(defn perform-in-sec
  "Schedule a function for execution in given seconds.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic."
  [opts sec execute-fn-sym & args]
  (enqueue opts (u/add-sec sec) execute-fn-sym args))

(defn perform-every
  "Schedule a function for periodic execution.
  If a cron entry already exists with the same name, it will be
  overwritten.
  `cron-schedule` should be a string in the UNIX cron format.
  `execute-fn-sym` should be a fully-qualified function symbol.
  `args` are variadic."
  [opts cron-opts execute-fn-sym & args]
  (register-cron-schedule opts cron-opts execute-fn-sym args))
