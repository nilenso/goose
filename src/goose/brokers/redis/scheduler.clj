(ns ^:no-doc goose.brokers.redis.scheduler
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.heartbeat :as heartbeat]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn run-at
  [redis-conn
   schedule-epoch-ms
   {:keys [ready-queue] :as job}]
  (let [scheduled-job (assoc job :schedule-run-at schedule-epoch-ms)]
    (if (< schedule-epoch-ms (u/epoch-time-ms))
      (redis-cmds/enqueue-front redis-conn ready-queue scheduled-job)
      (redis-cmds/enqueue-sorted-set redis-conn d/prefixed-schedule-queue schedule-epoch-ms scheduled-job))
    (select-keys job [:id])))

(defn- enqueue-due-scheduled-jobs
  "Returns truthy if due jobs were found."
  [redis-conn]
  (when-let [due-scheduled-jobs (redis-cmds/scheduled-jobs-due-now redis-conn d/prefixed-schedule-queue)]
    (redis-cmds/sorted-set->ready-queue
      redis-conn
      d/prefixed-schedule-queue
      due-scheduled-jobs
      job/ready-or-retry-queue)
    true))

(defn run
  [{:keys [internal-thread-pool redis-conn scheduler-polling-interval-sec]}]
  (log/info "Polling scheduled jobs...")
  (u/log-on-exceptions
    (u/while-pool
      internal-thread-pool
      (let [scheduled-jobs-found? (enqueue-due-scheduled-jobs redis-conn)
            cron-entries-found? (cron/enqueue-due-cron-entries redis-conn)]
        (when-not (or scheduled-jobs-found?
                      cron-entries-found?)
          ;; Goose only sleeps if no due jobs or cron entries are found.
          ;; If they are found, then Goose immediately polls to check
          ;; if more jobs are due.
          (let [global-workers-count (heartbeat/global-workers-count redis-conn)]
            (u/sleep scheduler-polling-interval-sec global-workers-count))))))
  (log/info "Stopped scheduler. Exiting gracefully..."))
