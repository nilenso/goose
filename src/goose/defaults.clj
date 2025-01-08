(ns goose.defaults
  "All default configurations for Goose are defined here."
  (:require
   [clojure.string :as str]))

(def worker-threads 5)
(def graceful-shutdown-sec 30)
(def content-type "ptaoussanis/nippy")

(def ^:no-doc queue-prefix "goose/queue:")
(def ^:no-doc in-progress-queue-prefix "goose/in-progress-jobs:")
(def ^:no-doc process-prefix "goose/processes:")
(def ^:no-doc heartbeat-prefix "goose/heartbeat:")
(def ^:no-doc batch-prefix "goose/batch:")

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
      (str/split (re-pattern (str queue-prefix "*")))
      (second)))

(defn ^:no-doc prefix-batch
  [id]
  (str batch-prefix id))

(def ^:no-doc prefixed-schedule-queue (prefix-queue schedule-queue))
(def ^:no-doc prefixed-retry-schedule-queue (prefix-queue schedule-queue))
(def ^:no-doc prefixed-dead-queue (prefix-queue dead-queue))
(def ^:no-doc prefixed-cron-queue (prefix-queue cron-queue))
(def ^:no-doc prefixed-cron-entries (str "goose/" cron-entries))

;;; ======== Redis defaults ========
(def redis-internal-threads 4)
(def redis-default-url "redis://localhost:6379")
(def redis-long-polling-timeout-sec 1)
(def redis-scheduler-polling-interval-sec 5)
(def redis-scheduled-jobs-pop-limit 50)
(def redis-cron-names-pop-limit 50)
(def redis-heartbeat-sleep-sec 15)
(def redis-heartbeat-expire-sec 60)
(def redis-producer-pool-opts
  {:max-total-per-key 5
   :max-idle-per-key  5
   :min-idle-per-key  1})
(defn redis-consumer-pool-opts
  [threads]
  {:max-total-per-key (+ redis-internal-threads threads)
   :max-idle-per-key  (+ redis-internal-threads threads)
   :min-idle-per-key  (inc redis-internal-threads)})

;;; ======== RabbitMQ defaults ========
(def rmq-default-url "amqp://guest:guest@localhost:5672")
(def rmq-producer-channels 5)
(def rmq-prefetch-limit 1)
(def rmq-delay-limit-ms 4294967295) ; (2^32 - 1)
(def rmq-classic-queue "classic")
(def rmq-quorum-queue "quorum")
(def rmq-replication-factor 3)
(def sync-confirms "sync")
(def async-confirms "classic")
(def ^:no-doc rmq-exchange "")
(def ^:no-doc rmq-delay-exchange prefixed-schedule-queue)
(def ^:no-doc rmq-delay-exchange-type "x-delayed-message")
(def ^:no-doc rmq-low-priority 0)
(def ^:no-doc rmq-high-priority 1)

;;; ======== Console ========
(def route-prefix "/goose")
(def app-name "Goose Console")
(def page-size 10)
(def page 1)
(def limit 10)
