(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.redis :refer [validate-redis]]
    [goose.validations.queue :refer [validate-queue]]))

(defn validate-worker-params
  [broker-opts queue threads
   graceful-shutdown-sec
   scheduler-polling-interval-sec]
  (validate-redis broker-opts)
  (validate-queue queue)
  (when-let
    [validation-error
     (cond
       (not (pos-int? threads))
       ["Thread count should be a positive integer" (u/wrap-error :threads-invalid threads)]

       (not (pos-int? graceful-shutdown-sec))
       ["Graceful shutdown should be a positive integer" (u/wrap-error :graceful-shutdown-sec-invalid graceful-shutdown-sec)]

       (not (pos-int? scheduler-polling-interval-sec))
       ["Scheduler polling interval should be a positive integer" (u/wrap-error :scheduler-polling-interval-sec-invalid scheduler-polling-interval-sec)])]
    (throw (apply ex-info validation-error))))
