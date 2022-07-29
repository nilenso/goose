(ns goose.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]))

(defmacro ^:no-doc log-on-exceptions
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
  ([] (System/currentTimeMillis))
  ([date] (inst-ms date)))

(defn ^:no-doc add-sec
  ([sec] (add-sec sec (epoch-time-ms)))
  ([sec epoch-time-millis]
   (+ (* 1000 sec) epoch-time-millis)))

(defmacro ^:no-doc while-pool
  [pool & body]
  `(while (not (cp/shutdown? ~pool))
     ~@body))

(defn ^:no-doc require-resolve
  [fn-sym]
  (-> fn-sym
      (str)
      (str/split #"/")
      (first)
      (symbol)
      (require))
  (resolve fn-sym))

(defn ^:no-doc arities
  [fn-sym]
  (->> fn-sym
       (resolve)
       (meta)
       (:arglists)
       (map count)))

(defn ^:no-doc hostname []
  (.getHostName (java.net.InetAddress/getLocalHost)))
