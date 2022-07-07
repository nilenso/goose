(ns goose.statsd.statsd
  (:require
    [clj-statsd :as statsd]))

(defonce default-opts
  {:host "127.0.0.1"
   :port 8125
   :sample-rate 1.0
   :tags        #{}})

(defonce statsd-prefix "goose.")
(defn initialize
  [{:keys [host port]}]
  (when (and host port)
    (statsd/setup host port :prefix statsd-prefix)))

(defn add-queue-tag
  [{:keys [tags sample-rate]} queue]
  {:sample-rate sample-rate
   :tags        (merge tags (str "queue:" queue))})

(defn add-function-tag
  [{:keys [tags sample-rate]} function]
  {:sample-rate sample-rate
   :tags (merge tags (str "function:" function))})

(defmacro timed-execution
  "Time the execution of the provided code."
  [rate tags & body]
  `(let [start# (System/currentTimeMillis)]
     (try
       ~@body
       (finally
         (statsd/timing "job.execution_time" (- (System/currentTimeMillis) start#) ~rate ~tags)))))

(defmacro emit-metrics
  [sample-rate tags run-fn]
  `(try
     (statsd/increment "jobs.count" 1 ~sample-rate ~tags)
     (timed-execution ~sample-rate ~tags ~run-fn)
     (statsd/increment "jobs.success" 1 ~sample-rate ~tags)
     (catch Exception ex#
       (statsd/increment "jobs.failure" 1 ~sample-rate ~tags)
       (throw ex#))))
