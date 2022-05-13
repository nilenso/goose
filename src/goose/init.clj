(ns goose.init)

; TODO: be strict around keys.
(defn init-client [{:keys [redis-url]
                    :or   {redis-url "redis://localhost:6379/"}}]
  {:redis-conn {:pool {} :spec {:uri redis-url}}})

(defn init-worker [{:keys [redis-url redis-pool-opts graceful-shutdown-time-sec parallelism]
                    :or   {redis-url                  "redis://localhost:6379/"
                           redis-pool-opts            {}
                           graceful-shutdown-time-sec 30
                           parallelism                1}}]
  {:redis-conn                 {:pool redis-pool-opts :spec {:uri redis-url}}
   :graceful-shutdown-time-sec graceful-shutdown-time-sec
   :parallelism                parallelism})
