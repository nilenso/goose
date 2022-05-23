(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.common :refer [validate-redis]]
    [goose.validations.queue :refer [queues-invalid?]]))

(defn gaceful-shutdown-time-sec-valid?
  [time]
  (or
    (not (int? time))
    (neg? time)))

(defn- parallelism-less-than-1?
  [num]
  (not (pos-int? num)))

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
       ["Parallelism isn't a positive integer" (u/wrap-error :parallelism-invalid parallelism)]

       (gaceful-shutdown-time-sec-valid? graceful-shutdown-time-sec)
       ["Invalid graceful shutdown time" (u/wrap-error :graceful-shutdown-time-sec-invalid graceful-shutdown-time-sec)])]
    (throw (apply ex-info validation-error))))
