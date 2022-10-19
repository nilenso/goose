(ns goose.cron.parsing
  (:import
    (com.cronutils.model CronType)
    (com.cronutils.model.definition CronDefinitionBuilder)
    (com.cronutils.model.time ExecutionTime)
    (com.cronutils.parser CronParser)
    (java.time ZonedDateTime ZoneId)))

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


(defn next-run-epoch-ms
  [cron-schedule timezone]
  (let [zone (ZoneId/of timezone)]
    (some-> (parse-cron cron-schedule)
            (ExecutionTime/forCron)
            (.nextExecution (ZonedDateTime/now zone))
            (.orElse nil)
            (.toInstant)
            (.toEpochMilli))))

(defn previous-run-epoch-ms
  [cron-schedule timezone]
  (let [zone (ZoneId/of timezone)]
    (some-> (parse-cron cron-schedule)
            (ExecutionTime/forCron)
            (.lastExecution (ZonedDateTime/now zone))
            (.orElse nil)
            (.toInstant)
            (.toEpochMilli))))
