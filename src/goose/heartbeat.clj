(ns goose.heartbeat
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]))

(defn process-set
  [queue]
  (str d/process-prefix queue))

(defn heartbeat-id
  [id]
  (str d/heartbeat-prefix id))

(defn alive?
  [redis-conn id]
  (boolean (r/get-key redis-conn (heartbeat-id id))))

(defn run
  [{:keys [internal-thread-pool
           id redis-conn queue]}]
  (r/add-to-set redis-conn (process-set queue) id)
  (u/while-pool
    internal-thread-pool
    (r/set-key-val redis-conn (heartbeat-id id) "alive" d/heartbeat-expire-sec)
    (Thread/sleep (* 1000 15))))

(defn stop
  [id redis-conn queue]
  (r/del-keys redis-conn [(str d/heartbeat-prefix id)])
  (r/del-from-set redis-conn (str d/process-prefix queue) id))
