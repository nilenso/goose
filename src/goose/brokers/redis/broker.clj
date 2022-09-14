(ns goose.brokers.redis.broker
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.api.dead-jobs :as dead-jobs]
    [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.cron :as cron]
    [goose.brokers.redis.scheduler :as redis-scheduler]
    [goose.brokers.redis.worker :as redis-worker]
    [goose.defaults :as d]))

(defrecord Redis [conn scheduler-polling-interval-sec]
  b/Broker
  (enqueue [this job]
    (redis-cmds/enqueue-back (:conn this) (:ready-queue job) job)
    (select-keys job [:id]))
  (schedule [this schedule job]
    (redis-scheduler/run-at (:conn this) schedule job))
  (register-cron [this cron-name cron-schedule job-description]
    (cron/register (:conn this) cron-name cron-schedule job-description))
  (start [this worker-opts]
    (redis-worker/start
      (assoc worker-opts
        :redis-conn (:conn this)
        :scheduler-polling-interval-sec (:scheduler-polling-interval-sec this))))

  ; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this]
    (enqueued-jobs/list-all-queues (:conn this)))
  (enqueued-jobs-size [this queue]
    (enqueued-jobs/size (:conn this) queue))
  (enqueued-jobs-find-by-id [this queue id]
    (enqueued-jobs/find-by-id (:conn this) queue id))
  (enqueued-jobs-find-by-pattern [this queue match? limit]
    (enqueued-jobs/find-by-pattern (:conn this) queue match? limit))
  (enqueued-jobs-prioritise-execution [this job]
    (enqueued-jobs/prioritise-execution (:conn this) job))
  (enqueued-jobs-delete [this job]
    (enqueued-jobs/delete (:conn this) job))
  (enqueued-jobs-purge [this queue]
    (enqueued-jobs/purge (:conn this) queue))

  ; scheduled-jobs API
  (scheduled-jobs-size [this]
    (scheduled-jobs/size (:conn this)))
  (scheduled-jobs-find-by-id [this id]
    (scheduled-jobs/find-by-id (:conn this) id))
  (scheduled-jobs-find-by-pattern [this match? limit]
    (scheduled-jobs/find-by-pattern (:conn this) match? limit))
  (scheduled-jobs-prioritise-execution [this job]
    (scheduled-jobs/prioritise-execution (:conn this) job))
  (scheduled-jobs-delete [this job]
    (scheduled-jobs/delete (:conn this) job))
  (scheduled-jobs-purge [this]
    (scheduled-jobs/purge (:conn this)))

  ; dead-jobs API
  (dead-jobs-size [this]
    (dead-jobs/size (:conn this)))
  (dead-jobs-pop [this]
    (dead-jobs/pop (:conn this)))
  (dead-jobs-find-by-id [this id]
    (dead-jobs/find-by-id (:conn this) id))
  (dead-jobs-find-by-pattern [this match? limit]
    (dead-jobs/find-by-pattern (:conn this) match? limit))
  (dead-jobs-re-enqueue-for-execution [this job]
    (dead-jobs/re-enqueue-for-execution (:conn this) job))
  (dead-jobs-delete [this job]
    (dead-jobs/delete (:conn this) job))
  (dead-jobs-delete-older-than [this epoch-time-ms]
    (dead-jobs/delete-older-than (:conn this) epoch-time-ms))
  (dead-jobs-purge [this]
    (dead-jobs/purge (:conn this)))

  ; cron entries API
  (cron-jobs-find-by-name [this entry-name]
    (cron/find-by-name (:conn this) entry-name))
  (cron-jobs-delete [this entry-name]
    (cron/delete (:conn this) entry-name))
  (cron-jobs-delete-all [this]
    (cron/delete-all (:conn this))))

(def default-opts
  "Default config for Redis client."
  {:url                            d/redis-default-url
   :pool-opts                      nil
   :scheduler-polling-interval-sec 5})

(defn- new-pool-opts
  [thread-count]
  (if thread-count
    {:max-total-per-key (+ d/redis-internal-thread-pool-size thread-count)
     :max-idle-per-key  (+ d/redis-internal-thread-pool-size thread-count)
     :min-idle-per-key  (inc d/redis-internal-thread-pool-size)}
    {:max-total-per-key d/redis-client-pool-size
     :max-idle-per-key  d/redis-client-pool-size
     :min-idle-per-key  1}))

(defn new
  "Create a client for Redis broker.
  If pooling-opts aren't provided,
  conneciton count will be derived from thread-count.
  When initializing broker for a worker, thread-count
  must be provided.
  For a client, thread-count can be ignored if throughput is less."
  ([opts] (goose.brokers.redis.broker/new opts nil))
  ([{:keys [url pool-opts scheduler-polling-interval-sec]} thread-count]
   (let [pool-opts (or pool-opts (new-pool-opts thread-count))]
     (->Redis {:spec {:uri url} :pool pool-opts}
              scheduler-polling-interval-sec))))
