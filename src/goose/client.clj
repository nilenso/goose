(ns goose.client
  (:require
    [goose.brokers.broker :as b]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.retry :as retry]
    [goose.utils :as u]))

(def default-opts
  "Default config for Goose client."
  {:queue      d/default-queue
   :retry-opts retry/default-opts})

(defn- register-cron-schedule
  [{:keys [broker queue retry-opts] :as _opts}
   cron-name
   cron-schedule
   execute-fn-sym
   args]
  (let [retry-opts     (retry/prefix-queue-if-present retry-opts)
        prefixed-queue (d/prefix-queue queue)]
    (:id (b/register-cron broker
                          cron-name
                          cron-schedule
                          (j/description execute-fn-sym
                                         args
                                         queue
                                         prefixed-queue
                                         retry-opts)))))

(defn- enqueue
  [{:keys [broker queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        prefixed-queue (d/prefix-queue queue)
        job (j/new execute-fn-sym args queue prefixed-queue retry-opts)]

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
  [opts date-time execute-fn-sym & args]
  (enqueue opts (u/epoch-time-ms date-time) execute-fn-sym args))

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
  [opts cron-name cron-schedule execute-fn-sym & args]
  (register-cron-schedule opts cron-name cron-schedule execute-fn-sym args))
