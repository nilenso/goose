(ns goose.utils
  (:refer-clojure :exclude [list])
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [taoensso.nippy :as nippy]))

(defn encode [x]
  (nippy/freeze x))

(defn decode [o]
  (nippy/thaw o))

(defmacro ^:no-doc log-on-exceptions
  "Catch any Exception from the body and log it."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (when-not (= "sleep interrupted" (ex-message e#))
         (log/error e# "Exception occurred")))))

(defn epoch-time-ms
  "Returns Unix epoch milliseconds for given date.
   If no date is given, returns epoch for current time."
  ([] (System/currentTimeMillis))
  ([date] (inst-ms date)))

(defn ^:no-doc sec-to-ms
  [sec]
  (* 1000 sec))

(defn ^:no-doc add-sec
  ([sec] (add-sec sec (epoch-time-ms)))
  ([sec epoch-time-millis]
   (+ (sec-to-ms sec) epoch-time-millis)))

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

(defn ^:no-doc random-element
  "Randomly select an element from a list & return it."
  [list]
  (if (zero? (count list))
    (throw (ex-info "List is empty." {:empty-list list}))
    (nth list (rand-int (count list)))))
