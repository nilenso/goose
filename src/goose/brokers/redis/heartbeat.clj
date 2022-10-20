(ns goose.brokers.redis.heartbeat
  ^:no-doc
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.defaults :as d]
    [goose.utils :as u]))

(defn heartbeat-id [id]
  (str d/heartbeat-prefix id))

(defn alive? [redis-conn id]
  (boolean (redis-cmds/get-key redis-conn (heartbeat-id id))))

(defn local-workers-count [redis-conn process-set]
  (redis-cmds/set-size redis-conn process-set))

(defn global-workers-count
  [redis-conn]
  (let [process-sets (redis-cmds/find-sets redis-conn (str d/process-prefix "*"))
        process-counts (map (fn [process] (local-workers-count redis-conn process)) process-sets)]
    (reduce + process-counts)))

(defn run
  [{:keys [internal-thread-pool id redis-conn process-set graceful-shutdown-sec]}]
  (redis-cmds/add-to-set redis-conn process-set id)
  (u/log-on-exceptions
    (u/while-pool
      internal-thread-pool
      ;; Goose stops sending heartbeat when shutdown is initialized.
      ;; Set expiry beyond graceful-shutdown time so in-progress jobs
      ;; aren't considered abandoned and double executions are avoided.
      (let [expiry (max d/redis-heartbeat-expire-sec graceful-shutdown-sec)]
        (redis-cmds/set-key-val redis-conn (heartbeat-id id) "alive" expiry))
      (u/sleep d/redis-heartbeat-sleep-sec))))

(defn stop
  [{:keys [id redis-conn process-set in-progress-queue]}]
  (redis-cmds/del-keys redis-conn [(str d/heartbeat-prefix id)])
  ;; `(redis-cmds/list-size in-progress-queue)` won't be empty
  ;; when jobs swallow thread-interrupted exception;
  ;; and don't exit in-time for graceful shutdown.
  ;; In such scenarios, don't delete process from set.
  ;; Orphan checker will recover half-executed job &
  ;; will delete process after all jobs have been recovered.
  (when (= 0 (redis-cmds/list-size redis-conn in-progress-queue))
    (redis-cmds/del-from-set redis-conn process-set id)))
