(ns goose.validations.client
  (:require
    [goose.utils :as u]))

(defn validate-perform-at-params
  [date-time]
  (when (not (inst? date-time))
    (throw
      (ex-info "date-time should be an instance of date object"
               (u/wrap-error :date-time-invalid date-time)))))

(defn validate-perform-in-sec-params
  [sec]
  (when (not (int? sec))
    (throw
      (ex-info "seconds should be an integer"
               (u/wrap-error :seconds-non-integer sec)))))
