(ns goose.validations.statsd
  (:require
    [goose.utils :as u]))

(defn validate-statsd
  [{:keys [host port sample-rate tags]}]
  (when-let
    [validation-error
     (cond
       (and host (not (string? host)))
       ["host should be a string" (u/wrap-error :invalid-host host)]

       (and port (not (pos-int? port)))
       ["port should be a positive integer" (u/wrap-error :invalid-port port)]

       (and sample-rate (not (double? sample-rate)))
       ["sample-rate should be a double" (u/wrap-error :invalid-sample-rate sample-rate)]

       (and tags (not (map? tags)))
       ["tags should be a map" (u/wrap-error :invalid-tags tags)])]
    (throw (apply ex-info validation-error))))
