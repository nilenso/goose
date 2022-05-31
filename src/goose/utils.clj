(ns goose.utils
  (:require
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]))

(defn wrap-error [error data]
  {:errors {error data}})

(defmacro log-on-exceptions
  "Catch any Exception from the body and log it."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e# "Exception occurred"))))

(defn epoch-time-ms
  "Returns Unix epoch time for given date.
   If no date is given, returns epoch for current time."
  ([] (epoch-time-ms (java.util.Date.)))
  ([date] (inst-ms date)))

(defmacro while-pool
  [pool & body]
  `(while (not (cp/shutdown? ~pool))
     ~@body))
