(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.redis :refer [validate-redis]]
    [goose.validations.queue :refer [validate-queue]]))

(defn gaceful-shutdown-time-sec-valid?
  [time]
  (or
    (not (int? time))
    (neg? time)))

(defn- threads-less-than-1?
  [num]
  (not (pos-int? num)))

(defn validate-worker-params
  [redis-url redis-pool-opts queue
   graceful-shutdown-time-sec threads]
  (validate-redis redis-url redis-pool-opts)
  (validate-queue queue)
  (when-let
    [validation-error
     (cond
       (threads-less-than-1? threads)
       ["Thread count isn't a positive integer" (u/wrap-error :threads-invalid threads)]

       (gaceful-shutdown-time-sec-valid? graceful-shutdown-time-sec)
       ["Graceful shutdown time isn't a positive integer" (u/wrap-error :graceful-shutdown-time-sec-invalid graceful-shutdown-time-sec)])]
    (throw (apply ex-info validation-error))))
