(ns goose.validations.statsd
  (:require
    [goose.utils :as u]))

(defn validate-statsd
  [{:keys [sample-rate tags]}]
  (when-let
    [validation-error
     (cond
       (not (double? sample-rate))
       ["sample-rate should be a double" (u/wrap-error :invalid-sample-rate sample-rate)]

       (not (set? tags))
       ["tags should be a set" (u/wrap-error :invalid-tags tags)])]
    (throw (apply ex-info validation-error))))
