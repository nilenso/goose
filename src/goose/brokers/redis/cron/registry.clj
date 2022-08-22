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

(defn set-due-time [cron-entry next-run-epoch-ms]
  (car/zadd d/prefixed-cron-schedules-queue "XX" next-run-epoch-ms cron-entry))

(defn- enqueue-jobs-to-ready-on-priority [jobs]
  (doseq [[queue-key grouped-jobs] (group-by :prefixed-queue jobs)]
    (apply car/rpush queue-key grouped-jobs)))

(defn enqueue-due-cron-entries
  [redis-conn due-cron-entries]
  (let [jobs-to-enqueue          (map (comp j/from-description :job-description) due-cron-entries)
        ;; The due times must be computed here because it is effectful
        ;; and shouldn't go in a transaction block.
        entries-by-next-due-time (group-by (comp cron-parsing/next-run-epoch-ms :cron-schedule)
                                           due-cron-entries)]
    (redis-cmds/with-transaction redis-conn
      (enqueue-jobs-to-ready-on-priority jobs-to-enqueue)
      (doseq [[due-epoch-ms cron-entries] entries-by-next-due-time
              cron-entry cron-entries]
        (set-due-time cron-entry due-epoch-ms)))))

(defn due-cron-entries
  [redis-conn]
  (not-empty
    (redis-cmds/wcar* redis-conn
      (car/zrangebyscore d/prefixed-cron-schedules-queue
                         redis-cmds/sorted-set-min
                         (u/epoch-time-ms)
                         "limit"
                         0
                         d/cron-entries-pop-limit))))

(defn find-and-enqueue-cron-entries
  "Returns truthy if due cron entries were found."
  [redis-conn]
  (when-let [due-cron-entries (due-cron-entries redis-conn)]
    (enqueue-due-cron-entries redis-conn due-cron-entries)
    true))