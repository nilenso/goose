(ns goose.defaults
  (:require
    [clojure.string :as string]))

(def long-polling-timeout-sec 1)
(def scheduled-jobs-pop-limit 50)
(def heartbeat-sleep-sec 15)
(def heartbeat-expire-sec 60)
(def scan-initial-cursor "0")

(def redis-internal-thread-pool-size 4)
(def redis-default-url "redis://localhost:6379")
(def redis-client-pool-size 5)

(def rmq-exchange "")
(def rmq-prefetch-limit 1)

(def queue-prefix "goose/queue:")
(def in-progress-queue-prefix "goose/in-progress-jobs:")
(def process-prefix "goose/processes:")
(def heartbeat-prefix "goose/heartbeat:")

(def default-queue "default")
(def schedule-queue "scheduled-jobs")
(def dead-queue "dead-jobs")

(def protected-queues [schedule-queue dead-queue])

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
