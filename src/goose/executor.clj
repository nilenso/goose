(ns goose.executor
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn execute-job
  [_ {:keys [id execute-fn-sym args]}]
  (apply (u/require-resolve execute-fn-sym) args)
  (log/debug "Executed job-id:" id))

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
      (when-let [job (r/dequeue-and-preserve redis-conn prefixed-queue in-progress-queue)]
        (call opts job)
        (r/remove-from-list redis-conn in-progress-queue job)))))
