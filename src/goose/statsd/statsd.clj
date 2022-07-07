(ns goose.statsd.statsd
  (:require
    [com.unbounce.dogstatsd.core :as statsd]))

(defonce default-opts
  {:sample-rate 1.0
   :tags        #{}})

(defn add-queue-tag
  [{:keys [tags sample-rate]} queue]
  {:sample-rate sample-rate
   :tags        (merge tags (str "queue:" queue))})

(defn add-function-tag
  [{:keys [tags sample-rate]} function]
  {:sample-rate sample-rate
   :tags (merge tags (str "function:" function))})

(defmacro emit-metrics
  [opts run-fn]
  `(try
     (statsd/increment "goose.jobs.count" ~opts)
     (statsd/time! ["goose.job.runtime" ~opts] ~run-fn)
     (statsd/increment "goose.jobs.success" ~opts)
     (catch Exception ex#
       (statsd/increment "goose.jobs.failure" ~opts)
       (throw ex#))))
