(ns goose.validations.statsd
  (:require
    [goose.utils :as u]))

(defn validate-statsd
  [{:keys [host port sample-rate tags]}]
  (when-let
    [validation-error
     (cond
       (not (string? host))
       ["host should be a string" (u/wrap-error :invalid-host host)]

       (not (pos-int? port))
       ["port should be a positive integer" (u/wrap-error :invalid-port port)]

       (not (double? sample-rate))
       ["sample-rate should be a double" (u/wrap-error :invalid-sample-rate sample-rate)]

       (not (set? tags))
       ["tags should be a set" (u/wrap-error :invalid-tags tags)])]
    (throw (apply ex-info validation-error))))
