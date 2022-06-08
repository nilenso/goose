(ns goose.defaults)

(def default-redis-url "redis://localhost:6379")
(def long-polling-timeout-sec 1)
(def scheduled-jobs-pop-limit 50)

(def process-prefix "goose/processes:")

(def heartbeat-prefix "goose/heartbeat:")
(def heartbeat-expire-sec 60)

(def queue-prefix "goose/queue:")
(def in-progress-queue-prefix "goose/in-progress-jobs:")

(def default-queue "default")
(def schedule-queue "scheduled-jobs")
(def dead-queue "dead-jobs")

(def protected-queues [schedule-queue dead-queue])
