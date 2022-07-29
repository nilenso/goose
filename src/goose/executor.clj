(ns goose.executor
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn execute-job
  [_ {:keys [execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args))

(defn preservation-queue
  [id]
  (str d/in-progress-queue-prefix id))

(defn run
  [{:keys [thread-pool redis-conn prefixed-queue in-progress-queue call]
    :as   opts}]
  (log/debug "Long-polling broker...")
  (u/while-pool
    thread-pool
    (u/log-on-exceptions
      (when-let [job (redis-cmds/dequeue-and-preserve redis-conn prefixed-queue in-progress-queue)]
        (call opts job)
        (redis-cmds/del-from-list redis-conn in-progress-queue job)))))
