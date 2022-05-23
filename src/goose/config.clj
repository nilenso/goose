(ns goose.config)

(def redis-url
  (let [host (or (System/getenv "REDIS_HOST") "localhost")
        port (or (System/getenv "REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(def long-polling-timeout-sec
  (* 5 60))

(def queue-prefix
  "goose/queue:")

(def default-queue
  "default")
