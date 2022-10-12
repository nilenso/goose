(ns goose.defaults
  (:require
    [clojure.string :as string]))

(def worker-threads 5)
(def graceful-shutdown-sec 30)
(def long-polling-timeout-sec 1)
(def scheduled-jobs-pop-limit 50)
(def cron-names-pop-limit 50)
(def heartbeat-sleep-sec 15)
(def heartbeat-expire-sec 60)
(def content-type "ptaoussanis/nippy")

(def queue-prefix "goose/queue:")
(def in-progress-queue-prefix "goose/in-progress-jobs:")
(def process-prefix "goose/processes:")
(def heartbeat-prefix "goose/heartbeat:")

(def default-queue "default")
(def schedule-queue "scheduled-jobs")
(def dead-queue "dead-jobs")
(def cron-queue "cron-schedules")
(def cron-entries "cron-entries")

(def protected-queues [schedule-queue dead-queue cron-queue cron-entries])

(defn ^:no-doc prefix-queue
  [queue]
  (str queue-prefix queue))

(defn ^:no-doc affix-queue
  [queue]
  (-> queue
      (string/split (re-pattern (str queue-prefix "*")))
      (second)))

(def prefixed-schedule-queue (prefix-queue schedule-queue))
(def prefixed-retry-schedule-queue (prefix-queue schedule-queue))
(def prefixed-dead-queue (prefix-queue dead-queue))
(def prefixed-cron-queue (prefix-queue cron-queue))
(def prefixed-cron-entries (str "goose/" cron-entries))

(def redis-internal-threads 4)
(def redis-default-url "redis://localhost:6379")
(def redis-scheduler-polling-interval-sec 5)
(def redis-producer-pool-opts
  {:max-total-per-key 5
   :max-idle-per-key  5
   :min-idle-per-key  1})
(defn redis-consumer-pool-opts
  [threads]
  {:max-total-per-key (+ redis-internal-threads threads)
   :max-idle-per-key  (+ redis-internal-threads threads)
   :min-idle-per-key  (inc redis-internal-threads)})

(def rmq-default-url "amqp://guest:guest@localhost:5672")
(def rmq-producer-channels 5)
(def rmq-exchange "")
(def rmq-delay-exchange (prefix-queue schedule-queue))
(def rmq-delay-exchange-type "x-delayed-message")
(def rmq-low-priority 0)
(def rmq-high-priority 1)
(def rmq-prefetch-limit 1)
(def rmq-delay-limit-ms 4294967295) ; (2^32 - 1)
(def rmq-classic-queue "classic")
(def rmq-quorum-queue "quorum")
(def sync-confirms :sync)
(def async-confirms :async)
