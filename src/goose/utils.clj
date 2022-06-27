(ns goose.utils
  (:require
    [clojure.string :as str]
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
       (when-not (= "sleep interrupted" (ex-message e#))
         (log/error e# "Exception occurred")))))

(defn epoch-time-ms
  "Returns Unix epoch time for given date.
   If no date is given, returns epoch for current time."
  ([] (epoch-time-ms (java.util.Date.)))
  ([date] (inst-ms date)))

(defn add-sec
  ([sec] (add-sec sec (epoch-time-ms)))
  ([sec epoch-time]
   (+ (* 1000 sec) epoch-time)))

(defmacro while-pool
  [pool & body]
  `(while (not (cp/shutdown? ~pool))
     ~@body))

(defn require-resolve
  [fn-sym]
  (-> fn-sym
      (str)
      (str/split #"/")
      (first)
      (symbol)
      (require))
  (resolve fn-sym))

(defn arities
  [fn-sym]
  (->> fn-sym
       (resolve)
       (meta)
       (:arglists)
       (map count)))

(defn hostname []
  (.getHostName (java.net.InetAddress/getLocalHost)))
