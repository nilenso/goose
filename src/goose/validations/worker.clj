(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.common :as common]))

(defn gaceful-shutdown-time-negative?
  [time]
  (neg? time))

(defn- parallelism-less-than-1?
  [num]
  (< num 1))

(defn validate-worker-params
  [redis-url redis-pool-opts graceful-shutdown-time-sec parallelism]
  (common/validate-redis redis-url redis-pool-opts)
  (when-let
    [validation-error
     (cond
       (parallelism-less-than-1? parallelism)
       ["Parallelism cannot be less than 1" (u/wrap-error :parallelism-invalid parallelism)]

       (gaceful-shutdown-time-negative? graceful-shutdown-time-sec)
       ["Graceful shutdown time should be greater than 10s" (u/wrap-error :graceful-shutdown-time-sec-invalid parallelism)])]
    (throw (apply ex-info validation-error))))

