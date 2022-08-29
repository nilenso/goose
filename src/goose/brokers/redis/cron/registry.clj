(ns goose.brokers.redis.cron.registry
  (:require [goose.defaults :as d]
            [goose.brokers.redis.commands :as redis-cmds]
            [taoensso.carmine :as car]
            [goose.job :as j]
            [goose.cron.parsing :as cron-parsing]
            [goose.utils :as u]))

(defn registry-entry [cron-schedule job-description]
  {:id              (str (random-uuid))
   :cron-schedule   cron-schedule
   :job-description job-description})

(defn register-cron
  [conn cron-schedule job-description]
  (let [entry             (registry-entry cron-schedule job-description)
        next-run-epoch-ms (cron-parsing/next-run-epoch-ms cron-schedule)]
    (redis-cmds/enqueue-sorted-set conn
                                   d/prefixed-cron-schedules-queue
                                   next-run-epoch-ms
                                   entry)
    entry))

(defn- set-due-time [cron-entry]
  (car/zadd d/prefixed-cron-schedules-queue
            "XX"
            "GT"
            (-> cron-entry
                :cron-schedule
                cron-parsing/next-run-epoch-ms)
            cron-entry))

(defn- enqueue-jobs-to-ready-on-priority [jobs]
  (doseq [[queue-key grouped-jobs] (group-by :prefixed-queue jobs)]
    (apply car/rpush queue-key grouped-jobs)))

(defn- due-cron-entries-command []
  (car/zrangebyscore d/prefixed-cron-schedules-queue
                     redis-cmds/sorted-set-min
                     (u/epoch-time-ms)
                     "limit"
                     0
                     d/cron-entries-pop-limit))

(defn due-cron-entries
  [redis-conn]
  (not-empty
    (redis-cmds/wcar* redis-conn
      (due-cron-entries-command))))

(defn enqueue-due-cron-entries
  "Returns truthy if due cron entries were found."
  [redis-conn]
  (redis-cmds/with-transaction redis-conn
    (car/watch d/prefixed-cron-schedules-queue)
    (let [due-cron-entries (not-empty (car/with-replies (due-cron-entries-command)))]
      (car/multi)
      (when due-cron-entries
        (let [jobs-to-enqueue (map (comp j/from-description :job-description) due-cron-entries)]
          (enqueue-jobs-to-ready-on-priority jobs-to-enqueue)
          (doseq [cron-entry due-cron-entries]
            (set-due-time cron-entry)))
        true))))