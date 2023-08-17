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
    [goose.defaults :as d]
    [goose.specs :as specs]))

(defrecord Redis [redis-conn opts]
  b/Broker
  (enqueue
    [this job]
    (redis-cmds/enqueue-back (:redis-conn this) (:ready-queue job) job)
    (select-keys job [:id]))
  (schedule [this schedule-epoch-ms job]
    (redis-scheduler/run-at (:redis-conn this) schedule-epoch-ms job))
  (register-cron [this cron-opts job-description]
    (cron/register (:redis-conn this) cron-opts job-description))
  (start-worker [this worker-opts]
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

  ;; cron entries API
  (cron-jobs-size [this]
    (cron/size (:redis-conn this)))
  (cron-jobs-find-by-name [this entry-name]
    (cron/find-by-name (:redis-conn this) entry-name))
  (cron-jobs-delete [this entry-name]
    (cron/delete (:redis-conn this) entry-name))
  (cron-jobs-purge [this]
    (cron/purge (:redis-conn this)))

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
  (dead-jobs-delete-older-than [this epoch-ms]
    (dead-jobs/delete-older-than (:redis-conn this) epoch-ms))
  (dead-jobs-purge [this]
    (dead-jobs/purge (:redis-conn this))))

(def default-opts
  "Map of sample config for Redis Message Broker.

  ### Keys
  `:url`       : URL to connect to Redis.\\
  [URL Syntax wiki](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details#uri-syntax)

  `:pool-opts` : Config for connection-pooling. Refer to `goose.specs.redis/pool-opts` for allowed spec of pool opts.\\
  Example      : [[goose.defaults/redis-producer-pool-opts]]"
  {:url       d/redis-default-url
   :pool-opts nil})

(defn new-producer
  "Creates a Redis broker implementation for client.

  ### Args
  `conn-opts`  : Config for connecting to Redis.\\
  Example      : [[default-opts]]

  ### Usage
  ```Clojure
  (new-producer redis-conn-opts)
  ```

  - [Redis Message Broker wiki](https://github.com/nilenso/goose/wiki/Redis)"
  [{:keys [url pool-opts] :as conn-opts}]
  (specs/assert-redis-producer conn-opts)
  (let [pool-opts (or pool-opts d/redis-producer-pool-opts)
        redis-conn (redis-connection/new url pool-opts)]
    (->Redis redis-conn nil)))

(defn new-consumer
  "Creates a Redis broker implementation for worker.

  ### Args
  `conn-opts`                       : Config for connecting to Redis.\\
  Example                           : [[default-opts]]

  `scheduler-polling-interval-sec`  : Interval at which to poll Redis for scheduled jobs.\\
  Acceptable values                 : `1-60`

  ### Usage
  ```Clojure
  (new-consumer redis-conn-opts 10)
  ```

  - [Redis Message Broker wiki](https://github.com/nilenso/goose/wiki/Redis)"
  ([conn-opts]
   (new-consumer conn-opts d/redis-scheduler-polling-interval-sec))
  ([conn-opts scheduler-polling-interval-sec]
   (specs/assert-redis-consumer conn-opts scheduler-polling-interval-sec)
   (let [opts (assoc conn-opts
                :scheduler-polling-interval-sec scheduler-polling-interval-sec)]
     ;; Connection to Redis is opened/closed from start/stop functions of worker.
     ;; This was done to avoid duplication of code & mis-match in `threads` config.
     (->Redis nil opts))))
