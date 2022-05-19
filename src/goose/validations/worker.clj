(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.common :refer [validate-redis]]
    [goose.validations.queue :refer [queues-invalid?]]))

(defn gaceful-shutdown-time-sec-negative?
  [time]
  (neg? time))

(defn- parallelism-less-than-1?
  [num]
  (< num 1))

(defn validate-worker-params
  [redis-url redis-pool-opts queues
   graceful-shutdown-time-sec parallelism]
  (validate-redis redis-url redis-pool-opts)
  (when-let
    [validation-error
     (cond
       (queues-invalid? queues)
       ["Invalid queues" (u/wrap-error :queues-invalid queues)]

       (parallelism-less-than-1? parallelism)
       ["Parallelism cannot be less than 1" (u/wrap-error :parallelism-invalid parallelism)]

       (gaceful-shutdown-time-sec-negative? graceful-shutdown-time-sec)
       ["Graceful shutdown time should be greater than 10s" (u/wrap-error :graceful-shutdown-time-sec-invalid parallelism)])]
    (throw (apply ex-info validation-error))))
