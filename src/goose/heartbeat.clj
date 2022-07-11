(ns goose.heartbeat
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]))

(defn heartbeat-id
  [id]
  (str d/heartbeat-prefix id))

(defn alive?
  [redis-conn id]
  (boolean (r/get-key redis-conn (heartbeat-id id))))

(defn process-count
  [redis-conn process-set]
  (r/set-size redis-conn process-set))

(defn run
  [{:keys [internal-thread-pool
           id redis-conn process-set
           graceful-shutdown-sec]}]
  (r/add-to-set redis-conn process-set id)
  (u/while-pool
    internal-thread-pool
    ; Goose stops sending heartbeat when shutdown is initialized.
    ; Set expiry beyond graceful-shutdown time so in-progress jobs
    ; aren't considered abandoned and double executions are avoided.
    (let [expiry (max d/heartbeat-expire-sec graceful-shutdown-sec)]
      (r/set-key-val redis-conn (heartbeat-id id) "alive" expiry)
      (Thread/sleep (* 1000 d/heartbeat-sleep-sec)))))

(defn stop
  [{:keys [id redis-conn process-set in-progress-queue]}]
  (r/del-keys redis-conn [(str d/heartbeat-prefix id)])
  ; If job has swallowed thread-interrupted exception,
  ; don't del the process from the queue.
  ; Job/process get recovered/cleaned-up by orphan-checker.
  (when (= 0 (r/list-size redis-conn in-progress-queue))
    (r/del-from-set redis-conn process-set id)))
