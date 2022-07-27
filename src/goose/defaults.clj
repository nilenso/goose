(ns goose.defaults
  (:require
    [clojure.string :as string]))

(defonce default-redis-url "redis://localhost:6379")
(defonce long-polling-timeout-sec 1)
(defonce client-redis-pool-size 5)
(defonce scheduled-jobs-pop-limit 50)
(defonce internal-thread-pool-size 4)
(defonce heartbeat-sleep-sec 15)
(defonce heartbeat-expire-sec 60)
(defonce scan-initial-cursor "0")

(defonce redis :redis)

(defonce queue-prefix "goose/queue:")
(defonce in-progress-queue-prefix "goose/in-progress-jobs:")
(defonce process-prefix "goose/processes:")
(defonce heartbeat-prefix "goose/heartbeat:")

(defonce default-queue "default")
(defonce schedule-queue "scheduled-jobs")
(defonce dead-queue "dead-jobs")

(defonce protected-queues [schedule-queue dead-queue])

(defn prefix-queue
  [queue]
  (str queue-prefix queue))

(defn affix-queue
  [queue]
  (-> queue
      (string/split (re-pattern (str queue-prefix "*")))
      (second)))

(defonce prefixed-schedule-queue (prefix-queue schedule-queue))
(defonce prefixed-retry-schedule-queue (prefix-queue schedule-queue))
(defonce prefixed-dead-queue (prefix-queue dead-queue))
