(ns goose.brokers.redis.scheduler
  ^:no-doc
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn run-at
  [redis-conn epoch-ms
   {:keys [ready-queue] :as job}]
  (let [scheduled-job (assoc job :schedule epoch-ms)]
    (if (< epoch-ms (u/epoch-time-ms))
      (redis-cmds/enqueue-front redis-conn ready-queue scheduled-job)
      (redis-cmds/enqueue-sorted-set redis-conn d/prefixed-schedule-queue epoch-ms scheduled-job))
    (select-keys job [:id])))

(defn- sleep-duration [redis-conn scheduler-polling-interval-sec]
  (let [total-process-count (heartbeat/total-process-count redis-conn)]
    ; Sleep for total-process-count * polling-interval + jitters
    ; Regardless of number of processes,
    ; On average, Goose checks for scheduled jobs
    ; every polling interval configured to reduce load on Redis.
    ; All worker processes must have same polling interval.
    (u/sec->ms
      (+ (* scheduler-polling-interval-sec total-process-count)
         (rand-int 3)))))

(defn- enqueue-due-scheduled-jobs
  "Returns truthy if due jobs were found."
  [redis-conn]
  (when-let [due-scheduled-jobs (redis-cmds/scheduled-jobs-due-now redis-conn d/prefixed-schedule-queue)]
    (redis-cmds/sorted-set->ready-queue
      redis-conn
      d/prefixed-schedule-queue
      due-scheduled-jobs
      job/ready-queue)
    true))

(defn run
  [{:keys [internal-thread-pool redis-conn
           scheduler-polling-interval-sec]}]
  (log/info "Polling scheduled jobs...")
  (u/log-on-exceptions
    (u/while-pool
      internal-thread-pool
      (let [scheduled-jobs-found? (enqueue-due-scheduled-jobs redis-conn)
            cron-entries-found? (cron/enqueue-due-cron-entries redis-conn)]
        (when-not (or scheduled-jobs-found?
                      cron-entries-found?)
          ; Goose only sleeps if no due jobs or cron entries are found.
          ; If they are found, then Goose immediately polls to check
          ; if more jobs are due.
          (Thread/sleep (sleep-duration redis-conn scheduler-polling-interval-sec))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
