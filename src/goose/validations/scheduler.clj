(ns goose.validations.scheduler
  (:require [goose.utils :as u]))

(defn validate-schedule
  [perform-at perform-in-sec]
  (when-let
    [validation-error
     (cond
       (not (or perform-at perform-in-sec))
       ["either :perform-at or :perform-in-sec should be present" (u/wrap-error :invalid-schedule "missing inputs")]

       (and perform-at perform-in-sec)
       [":perform-at & :perform-in-sec should be mutually exclusive" (u/wrap-error :invalid-schedule "conflicting inputs")]

       (when perform-in-sec (not (pos-int? perform-in-sec)))
       [":perform-in-sec should be positive integer" (u/wrap-error :perform-in-sec-invalid perform-in-sec)]

       (when perform-at (not (instance? java.util.Date perform-at)))
       [":perform-at should be an instance of date object" (u/wrap-error :perform-at-invalid perform-at)])]
    (throw (apply ex-info validation-error))))
