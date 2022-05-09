(ns goose.worker
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.string :as string]))

(defn- destructure-qualified-fn-sym [fn-sym]
  (as->
    fn-sym s
    (str s)
    (string/split s #"/")
    (map symbol s)))

(defn- execute-job
  [{:keys [id fn-sym args]}]
  (let
    [[namespace f] (destructure-qualified-fn-sym fn-sym)]
    (require namespace)
    (apply
      (resolve f)
      args))
  (println "Executed job-id: " id))

(defn- extract-job
  [list-member]
  (second list-member))

(defn- dequeue []
  (->
    cfg/default-queue
    (car/blpop cfg/long-polling-timeout-sec)
    (r/wcar*)
    (extract-job)))

(defn worker []
  (while true
    (println "Long-polling broker...")
    (when-let
      [job (dequeue)]
      (execute-job job))))