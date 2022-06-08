(ns goose.executor
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn- execute-job
  [redis-conn {:keys [id execute-fn-sym args] :as job}]
  (try
    (apply (u/require-resolve execute-fn-sym) args)
    (log/debug "Executed job-id:" id)
    (catch Exception ex
      (retry/handle-failure redis-conn job ex))))

(defn preservation-queue
  [id]
  (str d/in-progress-queue-prefix id))

(defn run
  [{:keys [thread-pool redis-conn
           prefixed-queue in-progress-queue]}]
  (u/while-pool
    thread-pool
    (log/info "Long-polling broker...")
    (u/log-on-exceptions
      (when-let [job (r/dequeue-and-preserve redis-conn prefixed-queue in-progress-queue)]
        (execute-job redis-conn job)
        (r/remove-from-list redis-conn in-progress-queue job))))
  (log/info "Stopped worker. Exiting gracefully..."))

