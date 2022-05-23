(ns goose.redis
  (:require
    [goose.config :as cfg]
    [goose.utils :as u]
    [taoensso.carmine :as car]))

(defn conn
  [url pool-opts]
  {:pool pool-opts :spec {:uri url}})

(defmacro wcar* [conn & body] `(car/wcar ~conn ~@body))

(defn dequeue [conn lists]
  ; Convert lists to vector because timeout should be last arg to blpop.
  (let [blpop-args (conj (vec lists) cfg/long-polling-timeout-sec)]
    (->> blpop-args
         (apply car/blpop)
         (wcar* conn))))

(defn enqueue [conn list element]
  (try
    (wcar* conn (car/rpush list element))
    (catch Exception e
      (throw
        (ex-info "" (u/wrap-error "Error enqueuing to redis" {:redis-error (.getMessage e)}))))))

(defn enqueue-with-expiry [conn list element expiry-sec]
  (enqueue conn list element)
  (wcar* conn (car/expire list expiry-sec)))
