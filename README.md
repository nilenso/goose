Goose: The sidekick for Clojure
=========

Simple, Scalable background processing library for Clojure.

Features
---------

*[Simplicity is Complicated.](https://youtu.be/rFejpH_tAHM)* We've strived to make Goose as simple, transparent & functional as possible.

- A *simple*, *functional* interface
- Flexible - *open* for extension, **closed** for modification
- Lean library, using latest Clojure tooling
- Configurable, with *sane defaults*
- Parallel execution using Java thread-pools
- **Graceful shutdown**
- Tested 🙂

Getting Started
---------

**Note:** All configurations are optional. Goose defaults to values mentioned in Configuration Options.

### Client

```clojure
(ns my-server
  (:require
    [goose.client :as c]))

(defn my-background-fn [arg1 arg2]
  (println "called with" arg1 arg2))

; Use default config.
(c/async c/default-opts `my-background-fn "foo" :bar)

; Modify few client configs.
(def custom-client-opts
  (assoc c/default-opts 
    :redis-url "redis://username:password@my.redis:6379"
    :queue "my-queue"))

(c/async custom-client-opts `my-background-fn "foo" :bar)

```

### Worker

```clojure
(ns my-worker
  (:require
    [goose.worker :as w]))

; Use default config.
(let [worker (w/start w/default-opts)]
  ; ... listen for SIGINT to shutdown gracefully
  (stop worker))

; Modify all worker configs.
(def custom-worker-opts
  {:redis-url                  "redis://username:password@my.redis:6379"
   :redis-pool-opts            {}
   :queue                      "my-queue"
   :threads                    5
   :graceful-shutdown-time-sec 60})

(let [configured-worker (w/start custom-worker-opts)]
  ; ... listen for SIGINT to shutdown gracefully
  (stop configured-worker))
```

Configuration options
---------

| Option | For? | Default Value | Description |
| --- | --- | --- | --- |
| `:redis-url` | Both | `redis://localhost:6379` | URL for Redis. Valid URL is: `redis://username:password@hostname:0-65353` |
| `:queue` | Both | `default` | Queue in redis which will be enqueued |
| `:retries` | Client | `21` | Number of times a job should be retried with exponential backoff, before marking it as dead |
| `:threads` | Worker | 1 | Number of threads in the threadpool |

**TODO: Fill in all config options before open sourcing.**

Why the name "Goose"?
---------

![goose-logo](link-to-goose-logo)

**Note: Insert a *Clojury* logo here**

Goose library is named after [Nick 'Goose' Bradshaw](https://historica.fandom.com/wiki/Nick_Bradshaw), the sidekick
to [Captain Pete 'Maverick' Mitchell](https://topgun.fandom.com/wiki/Pete_Mitchell) in Top Gun.

License
---------

Please see [LICENSE](https://github.com/nilenso/goose/blob/main/LICENSE) for licensing details.

