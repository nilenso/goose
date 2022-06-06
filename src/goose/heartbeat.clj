(ns goose.heartbeat
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]))

(defn start
  [{:keys [id redis-conn queue]}]
  (r/add-to-set redis-conn (str d/process-prefix queue) id)
  (r/set-key-val redis-conn (str d/heartbeat-prefix id) "alive" d/heartbeat-expire-sec))

(defn recur
  [{:keys [id redis-conn]}]
  (u/while-pool
    (Thread/sleep (* 1000 15))
    (r/set-key-val redis-conn (str d/heartbeat-prefix id) "alive" d/heartbeat-expire-sec)))

(defn stop
  [id redis-conn queue]
  (r/del-keys redis-conn [(str d/heartbeat-prefix id)])
  (r/del-from-set redis-conn (str d/process-prefix queue) id))
