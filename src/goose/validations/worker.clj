(ns goose.validations.worker
  (:require
    [goose.utils :as u]
    [goose.validations.statsd :refer [validate-statsd]]))

(defn validate-worker-params
  [threads statsd-opts
   graceful-shutdown-sec scheduler-polling-interval-sec]
  (validate-statsd statsd-opts)
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
