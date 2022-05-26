(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.redis :refer [validate-redis]]
    [goose.validations.queue :refer [validate-queue]]))

(defn validate-worker-params
  [redis-url redis-pool-opts queue
   scheduled-jobs-polling-interval-sec
   graceful-shutdown-time-sec threads]
  (validate-redis redis-url redis-pool-opts)
  (validate-queue queue)
  (when-let
    [validation-error
     (cond
       (not (pos-int? threads))
       ["Thread count isn't a positive integer" (u/wrap-error :threads-invalid threads)]

       (not (pos-int? graceful-shutdown-time-sec))
       ["Graceful shutdown time isn't a positive integer" (u/wrap-error :graceful-shutdown-time-sec-invalid graceful-shutdown-time-sec)]

       (not (pos-int? scheduled-jobs-polling-interval-sec))
       ["Scheduled jobs polling interval isn't a positive integer" (u/wrap-error :scheduled-jobs-polling-interval-sec-invalid scheduled-jobs-polling-interval-sec)])]
    (throw (apply ex-info validation-error))))
