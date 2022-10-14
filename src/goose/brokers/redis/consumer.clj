(ns goose.brokers.redis.consumer
  ^:no-doc
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn preservation-queue
  [id]
  (str d/in-progress-queue-prefix id))

(defn run
  [{:keys [thread-pool redis-conn ready-queue in-progress-queue call]
    :as   opts}]
  (log/debug "Long-polling broker...")
  (u/while-pool
    thread-pool
    (u/log-on-exceptions
      (when-let [job (redis-cmds/dequeue-and-preserve redis-conn ready-queue in-progress-queue)]
        (call opts job)
        (redis-cmds/del-from-list redis-conn in-progress-queue job)))))
