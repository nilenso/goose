(ns goose.defaults
  (:require
    [clojure.string :as string]))

(def default-redis-url "redis://localhost:6379")
(def long-polling-timeout-sec 1)
(def client-redis-pool-size 5)
(def scheduled-jobs-pop-limit 50)
(def internal-thread-pool-size 4)
(def heartbeat-sleep-sec 15)
(def heartbeat-expire-sec 60)
(def scan-initial-cursor "0")

(def redis :redis)

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

; Test if defonce is documented in cljdoc.org
(defonce test-var nil)
