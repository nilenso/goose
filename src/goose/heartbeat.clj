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
  (r/size-of-set redis-conn process-set))

(defn run
  [{:keys [internal-thread-pool
           id redis-conn process-set]}]
  (r/add-to-set redis-conn process-set id)
  (u/while-pool
    internal-thread-pool
    (r/set-key-val redis-conn (heartbeat-id id) "alive" d/heartbeat-expire-sec)
    (Thread/sleep (* 1000 15))))

(defn stop
  [id redis-conn process-set]
  (r/del-keys redis-conn [(str d/heartbeat-prefix id)])
  (r/del-from-set redis-conn process-set id))
