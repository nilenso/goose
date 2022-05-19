(ns goose.config)

(def default-redis-url
  "redis://localhost:6379")

(def long-polling-timeout-sec
  (* 5 60))

(def queue-prefix
  "goose/queue:")

(def default-queue
  "default")
