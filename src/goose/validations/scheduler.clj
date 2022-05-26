(ns goose.validations.scheduler
  (:require [goose.utils :as u]))


(defn- perform-in-sec-invalid?
  [seconds]
  (when seconds
    (not (pos-int? seconds))))

(defn- perform-at-invalid?
  [date]
  (when date
    (not (instance? java.util.Date date))))

(defn- mutually-inexclusive?
  [x y]
  (and x y))

(defn validate-schedule
  [{:keys [perform-at perform-in-sec]}]
  (when-let
    [validation-error
     (cond
       (mutually-inexclusive? perform-at perform-in-sec)
       [":perform-at & :perform-in-sec are mutually exclusive options" (u/wrap-error :invalid-schedule "conflicting inputs")]

       (perform-in-sec-invalid? perform-in-sec)
       [":perform-in-sec isn't a positive integer" (u/wrap-error :perform-in-sec-invalid perform-in-sec)]

       (perform-at-invalid? perform-at)
       [":perform-at isn't an instance of date object" (u/wrap-error :perform-at-invalid perform-at)])]
    (throw (apply ex-info validation-error))))
