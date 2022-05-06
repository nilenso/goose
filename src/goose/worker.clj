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
  [{:keys [id fn-sym args retries]}]
  (let
    [[ns f] (destructure-qualified-fn-sym fn-sym)]
    (require ns)
    (apply
      (resolve f)
      args))
  ; TODO: If you catch an exception, re-enqueue with decreased retries.
  (println "Executed job-id: " id))

(defn- dequeue []
  (r/wcar* (car/blpop cfg/default-queue cfg/long-polling-timeout-sec)))

(defn worker []
  (while true
    (println "Long-polling broker...")
    (let
      [job (dequeue)]
      (when job
        (execute-job (second job))))))