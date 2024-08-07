(ns goose.utils
  (:refer-clojure :exclude [list])
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [taoensso.nippy :as nippy])
  (:import
    (java.net InetAddress)
    (java.time Instant)
    (java.lang Math)))

(defn encode
  "Serializes input to a byte array using `taoensso.nippy/freeze`.\\
   To freeze custom types, extend the Clojure reader or use `taoensso.nippy/extend-freeze`."
  [x]
  (nippy/freeze x))

(defn encode-to-str
  "Serializes input to string using `taoensso.nippy/freeze-to-string`"
  [x]
  (nippy/freeze-to-string x))

(defn decode
  "Deserializes a frozen Nippy byte array to its original Clojure data type.\\
   To thaw custom types, extend the Clojure reader or use `taoensso.nippy/extend-thaw`."
  [o]
  (nippy/thaw o))

(defn decode-from-str
  "Deserialize from a frozen Nippy string to its original Clojure data type."
  [x]
  (nippy/thaw-from-string x))

(defmacro ^:no-doc log-on-exceptions
  "Catch any Exception from the body and log it."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (when-not (= "sleep interrupted" (ex-message e#))
         (log/error e# "Exception occurred")))))

(defn ^:no-doc epoch-time-ms
  "Returns Unix epoch milliseconds for given java.time.Instant.
   If no instant is given, returns epoch for current time."
  ([] (System/currentTimeMillis))
  ([instant] (.toEpochMilli ^Instant instant)))

(defn- sec->ms [sec]
  (* 1000 sec))

(defn ^:no-doc sec+current-epoch-ms
  ([sec] (+ (sec->ms sec) (epoch-time-ms))))

(defn ^:no-doc sleep
  "Sleep for given seconds, multiplied by count.
  Sleep duration: (seconds * count) + jitters"
  ([sec]
   (Thread/sleep ^long (sec->ms sec)))
  ([sec multiplier-count]
   ;; For sec=3, multiplier-count=5, it sleeps for
   ;; [15,000, 16,000) milliseconds.
   (Thread/sleep ^long (+ (sec->ms (* sec multiplier-count))
                          (rand-int (sec->ms 1))))))

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
  (.getHostName (InetAddress/getLocalHost)))

(defn ^:no-doc random-element
  "Randomly select an element from a list & return it."
  [list]
  (if (zero? (count list))
    (throw (ex-info "List is empty." {:empty-list list}))
    (nth list (rand-int (count list)))))

(defn ^:no-doc with-retry* [retry-count retry-delay-ms fn-to-retry]
  (let [res (try
              (fn-to-retry)
              (catch Exception e
                (if (< 0 retry-count)
                  e
                  (throw e))))]
    (if (instance? Throwable res)
      (do
        (log/warnf "Exception caught: %s. Retrying in %dms." res retry-delay-ms)
        (Thread/sleep ^long retry-delay-ms)
        (recur (dec retry-count) retry-delay-ms fn-to-retry))
      res)))

(defmacro ^:no-doc with-retry [{:keys [count retry-delay-ms]} & body]
  `(with-retry* ~count ~retry-delay-ms (fn [] ~@body)))

(defn ^:no-doc relative-time
  "Calculates the relative time from the given epoch milliseconds to the current time, returning a human-readable string."
  [epoch-ms]
  (let [now (epoch-time-ms)
        diff (- epoch-ms now)
        abs-diff (Math/abs diff)
        seconds (quot abs-diff 1000)
        minutes (quot seconds 60)
        hours (quot minutes 60)
        days (quot hours 24)
        pre-text (if (pos? diff) "in " "overdue by ")]
    (cond
      (> days 1) (str pre-text days " days")
      (= days 1) (str pre-text days " day")
      (> hours 1) (str pre-text hours " hours")
      (= hours 1) (str pre-text hours " hour")
      (> minutes 1) (str pre-text minutes " minutes")
      (= minutes 1) (str pre-text minutes " minute")
      (> seconds 1) (str pre-text seconds " seconds")
      (= seconds 1) (str pre-text seconds " second")
      :else "just now")))

(defn describe-cron
  "Describe cron schedule string in english"
  [cron-string]
  (let [[minute hour day-of-month month day-of-week] (str/split cron-string #"\s+")]
    (str "Runs "
         (cond
           (= minute "*") "every minute"
           (re-matches #"\*/\d+" minute) (str "every " (subs minute 2) " minutes")
           (= minute "0") ""
           :else (str "at minute " minute))
         (cond
           (= hour "*") " of every hour"
           (re-matches #"\*/\d+" hour) (str " every " (subs hour 2) " hours")
           (not= hour "0") (str " at hour " hour)
           :else "")
         (cond
           (= day-of-month "*") " every day"
           (re-matches #"\*/\d+" day-of-month) (str " every " (subs day-of-month 2) " days")
           :else (str " on day " day-of-month))
         (cond
           (= month "*") " of every month"
           (re-matches #"\*/\d+" month) (str " every " (subs month 2) " months")
           :else (str " in " ({"1" "January", "2" "February", "3" "March", "4" "April",
                               "5" "May", "6" "June", "7" "July", "8" "August",
                               "9" "September", "10" "October", "11" "November", "12" "December"}
                              month month)))
         (cond
           (= day-of-week "*") ""
           (re-matches #"\*/\d+" day-of-week) (str " every " (subs day-of-week 2) " days of the week")
           :else (str " on " ({"0" "Sunday", "1" "Monday", "2" "Tuesday", "3" "Wednesday",
                               "4" "Thursday", "5" "Friday", "6" "Saturday"} day-of-week day-of-week)))
         "")))
