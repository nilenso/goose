(ns goose.brokers.redis.client
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.api.dead-jobs :as dead-jobs]
    [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
    [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.brokers.redis.scheduler :as redis-scheduler]
    [goose.brokers.redis.worker :as redis-worker]
    [goose.defaults :as d]))

(defrecord Redis [conn scheduler-polling-interval-sec]
  b/Broker
  (enqueue [this job]
    (redis-cmds/enqueue-back (:conn this) (:prefixed-queue job) job))
  (schedule [this schedule job]
    (redis-scheduler/run-at (:conn this) schedule job))
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
  (enqueued-jobs-delete-all [this queue]
    (enqueued-jobs/delete-all (:conn this) queue))

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
  (scheduled-jobs-delete-all [this]
    (scheduled-jobs/delete-all (:conn this)))

  ; dead-jobs API
  (dead-jobs-size [this]
    (dead-jobs/size (:conn this)))
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
  (dead-jobs-delete-all [this]
    (dead-jobs/delete-all (:conn this))))

(def default-opts
  "Default config for Redis client."
  {:type                           d/redis
   :url                            d/redis-default-url
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

(defmethod b/new d/redis new-redis-broker
  ([opts] (b/new opts nil))
  ([{:keys [url pool-opts scheduler-polling-interval-sec]} thread-count]
   (let [pool-opts (or pool-opts (new-pool-opts thread-count))]
     (Redis. {:spec {:uri url} :pool pool-opts}
             scheduler-polling-interval-sec))))
