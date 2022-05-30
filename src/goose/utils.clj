(ns goose.utils
  (:require
    [clojure.tools.logging :as log]))

(defn wrap-error [error data]
  {:errors {error data}})

(defmacro log-on-exceptions
  "Catch any Exception from the body and log it."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e# "Exception occurred"))))

(defn epoch-time
  "Returns Unix epoch time for given date.
   If no date is given, returns epoch for current time."
  ([] (epoch-time (java.util.Date.)))
  ([date] (quot (inst-ms date) 1000)))
