(ns goose.init)

; TODO: be strict around keys.
; Validation: Redis URL should conform to regex: redis://redistogo:pass@panga.redistogo.com:9475/
(defn init-client [& {:keys [redis-url
                           redis-pool-opts
                           retries]
                    :or   {redis-url "redis://localhost:6379/"
                           redis-pool-opts {}
                           retries 0}}]
  {:redis-conn {:pool redis-pool-opts :spec {:uri redis-url}}
   :retries    retries})

; Allow option to set threadpool to false for benchmarking?
(defn init-worker [& {:keys [redis-url
                             redis-pool-opts
                             graceful-shutdown-time-sec
                             parallelism]
                      :or   {redis-url                  "redis://localhost:6379/"
                             redis-pool-opts            {}
                             graceful-shutdown-time-sec 30
                             parallelism                1}}]
  {:redis-conn                 {:pool redis-pool-opts :spec {:uri redis-url}}
   :graceful-shutdown-time-sec graceful-shutdown-time-sec
   ; Reason: TODO Github issue.
   :long-polling-timeout-sec   (quot graceful-shutdown-time-sec 3)
   :parallelism                parallelism})
