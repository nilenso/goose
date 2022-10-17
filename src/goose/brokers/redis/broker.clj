(ns goose.brokers.redis.broker
  (:require
    [goose.broker :as b]
    [goose.brokers.redis.api.dead-jobs :as dead-jobs]
    [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.connection :as redis-connection]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.scheduler :as redis-scheduler]
    [goose.brokers.redis.worker :as redis-worker]
    [goose.defaults :as d]))

(defrecord Redis [redis-conn opts]
  b/Broker
  (enqueue
    [this job]
    (redis-cmds/enqueue-back (:redis-conn this) (:ready-queue job) job)
    (select-keys job [:id]))
  (schedule [this schedule job]
    (redis-scheduler/run-at (:redis-conn this) schedule job))
  (register-cron [this cron-name cron-schedule job-description]
    (cron/register (:redis-conn this) cron-name cron-schedule job-description))
  (start [this worker-opts]
    (redis-worker/start (merge worker-opts (:opts this))))

  ;; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this]
    (enqueued-jobs/list-all-queues (:redis-conn this)))
  (enqueued-jobs-size [this queue]
    (enqueued-jobs/size (:redis-conn this) queue))
  (enqueued-jobs-find-by-id [this queue id]
    (enqueued-jobs/find-by-id (:redis-conn this) queue id))
  (enqueued-jobs-find-by-pattern [this queue match? limit]
    (enqueued-jobs/find-by-pattern (:redis-conn this) queue match? limit))
  (enqueued-jobs-prioritise-execution [this job]
    (enqueued-jobs/prioritise-execution (:redis-conn this) job))
  (enqueued-jobs-delete [this job]
    (enqueued-jobs/delete (:redis-conn this) job))
  (enqueued-jobs-purge [this queue]
    (enqueued-jobs/purge (:redis-conn this) queue))

  ;; scheduled-jobs API
  (scheduled-jobs-size [this]
    (scheduled-jobs/size (:redis-conn this)))
  (scheduled-jobs-find-by-id [this id]
    (scheduled-jobs/find-by-id (:redis-conn this) id))
  (scheduled-jobs-find-by-pattern [this match? limit]
    (scheduled-jobs/find-by-pattern (:redis-conn this) match? limit))
  (scheduled-jobs-prioritise-execution [this job]
    (scheduled-jobs/prioritise-execution (:redis-conn this) job))
  (scheduled-jobs-delete [this job]
    (scheduled-jobs/delete (:redis-conn this) job))
  (scheduled-jobs-purge [this]
    (scheduled-jobs/purge (:redis-conn this)))

  ;; dead-jobs API
  (dead-jobs-size [this]
    (dead-jobs/size (:redis-conn this)))
  (dead-jobs-pop [this]
    (dead-jobs/pop (:redis-conn this)))
  (dead-jobs-find-by-id [this id]
    (dead-jobs/find-by-id (:redis-conn this) id))
  (dead-jobs-find-by-pattern [this match? limit]
    (dead-jobs/find-by-pattern (:redis-conn this) match? limit))
  (dead-jobs-replay-job [this job]
    (dead-jobs/replay-job (:redis-conn this) job))
  (dead-jobs-replay-n-jobs [this n]
    (dead-jobs/replay-n-jobs (:redis-conn this) n))
  (dead-jobs-delete [this job]
    (dead-jobs/delete (:redis-conn this) job))
  (dead-jobs-delete-older-than [this epoch-time-ms]
    (dead-jobs/delete-older-than (:redis-conn this) epoch-time-ms))
  (dead-jobs-purge [this]
    (dead-jobs/purge (:redis-conn this)))

  ;; cron entries API
  (cron-jobs-find-by-name [this entry-name]
    (cron/find-by-name (:redis-conn this) entry-name))
  (cron-jobs-delete [this entry-name]
    (cron/delete (:redis-conn this) entry-name))
  (cron-jobs-delete-all [this]
    (cron/delete-all (:redis-conn this))))

(def default-opts
  "Default config for Redis client."
  {:url       d/redis-default-url
   :pool-opts nil})

(defn new-producer
  "Create a client that produce messages to Redis broker."
  [{:keys [url pool-opts]}]
  (let [pool-opts (or pool-opts d/redis-producer-pool-opts)
        redis-conn (redis-connection/new url pool-opts)]
    (->Redis redis-conn nil)))

(defn new-consumer
  "Create a Redis broker implementation for worker.
  The connection is opened & closed with start & stop of worker.
  To avoid duplication & mis-match in `threads` config,
  we decided to delegate connection creation at start time of worker."
  ([conn-opts]
   (new-consumer conn-opts d/redis-scheduler-polling-interval-sec))
  ([conn-opts scheduler-polling-interval-sec]
   (let [opts (assoc conn-opts
                :scheduler-polling-interval-sec scheduler-polling-interval-sec)]
     (->Redis nil opts))))
