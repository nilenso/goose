(ns goose.cron.parsing
  (:import (com.cronutils.model.definition CronDefinitionBuilder)
           (com.cronutils.model CronType)
           (com.cronutils.parser CronParser)
           (java.time ZonedDateTime)
           (com.cronutils.model.time ExecutionTime)))

(defn parse-cron
  [cron-schedule]
  (-> (CronDefinitionBuilder/instanceDefinitionFor CronType/UNIX)
      (CronParser.)
      (.parse cron-schedule)))

(defn valid-cron?
  [cron-schedule]
  (try
    (parse-cron cron-schedule)
    true
    (catch IllegalArgumentException _
      false)))


(defn next-run-epoch-ms [cron-schedule]
  (some-> (parse-cron cron-schedule)
          (ExecutionTime/forCron)
          (.nextExecution (ZonedDateTime/now))
          (.orElse nil)
          (.toInstant)
          (.toEpochMilli)))

(defn previous-run-epoch-ms [cron-schedule]
  (some-> (parse-cron cron-schedule)
          (ExecutionTime/forCron)
          (.lastExecution (ZonedDateTime/now))
          (.orElse nil)
          (.toInstant)
          (.toEpochMilli)))
