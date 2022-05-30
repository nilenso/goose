(ns goose.validations.scheduler
  (:require [goose.utils :as u]))

(defn validate-schedule
  [{:keys [perform-at perform-in-sec]}]
  (when-let
    [validation-error
     (cond
       (and perform-at perform-in-sec)
       [":perform-at & :perform-in-sec are mutually exclusive options" (u/wrap-error :invalid-schedule "conflicting inputs")]

       (when perform-in-sec (not (pos-int? perform-in-sec)))
       [":perform-in-sec isn't a positive integer" (u/wrap-error :perform-in-sec-invalid perform-in-sec)]

       (when perform-at (not (instance? java.util.Date perform-at)))
       [":perform-at isn't an instance of date object" (u/wrap-error :perform-at-invalid perform-at)])]
    (throw (apply ex-info validation-error))))
