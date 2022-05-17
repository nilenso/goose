(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.common :as common]))

(defn gaceful-shutdown-time-less-than-10s?
  [time]
  (< time 10))

(defn- parallelism-less-than-1?
  [num]
  (< num 1))

(defn validate-worker-params
  [opts]
  (common/validate-redis opts)
  (when-let
    [validation-error
     (cond
       (parallelism-less-than-1? (:parallelism opts))
       ["Parallelism cannot be less than 1" (u/wrap-error :parallelism-invalid (:parallelism opts))]

       (gaceful-shutdown-time-less-than-10s? (:graceful-shutdown-time-sec opts))
       ["Graceful shutdown time should be greater than 10s" (u/wrap-error :parallelism-invalid (:parallelism opts))])]
    (throw (apply ex-info validation-error))))

