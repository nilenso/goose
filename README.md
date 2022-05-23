Goose: The sidekick for Clojure
=========

Simple, Scalable background processing library for Clojure.

Features
---------

- A *simple*, *functional* interface
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
(ns my-application
  (:require
    [goose.client :as c]))

(defn my-background-fn [arg1 arg2]
  (println "called with" arg1 arg2))

; Use default config.
(c/async nil `my-background-fn "foo" :bar)

(def goose-client-opts
  {:redis-url "redis://username:password@my.redis:6379"
   :queue "my-queue"})

(c/async goose-client-opts `my-background-fn "foo" :bar)

```

### Worker

```clojure
(ns my-application-worker
  (:require
    [goose.worker :as w]))

; Use default config.
(let [worker (w/start nil)]
  ; ... listen for SIGINT to shutdown gracefully
  (stop worker))

(def goose-worker-opts
  {:redis-url "redis://username:password@my.redis:6379"
   :queues '("my-queue")
   :parallelism 5
   :graceful-shutdown-time-sec 60})

(let [configured-worker (w/start goose-worker-opts)]
  ; ... listen for SIGINT to shutdown gracefully
  (stop configured-worker))
```

Configuration options
---------

| Option | For? | Default Value | Description |
| --- | --- | --- | --- |
| `:redis-url` | Both | `redis://localhost:6379` | URL for Redis. Valid URL is: `redis://username:password@hostname:0-65353` |
| `:queue` | Client | `default` | Queue in redis which will be enqueued |
| `:queues` | Worker | `["default"]` | Queues for worker to read from |
| `:parallelism` | Worker | 1 | Number of threads running in parallel |

**Note: Config options are yet to be completed**

Why the name "Goose"?
---------

![goose-logo](https://upload.wikimedia.org/wikipedia/commons/3/31/Goose_Up_Close.jpg)

**Note: Insert a *Clojury* logo here**

Goose library is named after [Nick 'Goose' Bradshaw](https://historica.fandom.com/wiki/Nick_Bradshaw), the sidekick
to [Captain Pete 'Maverick' Mitchell](https://topgun.fandom.com/wiki/Pete_Mitchell) in Top Gun.

License
---------

Please see [LICENSE](https://github.com/nilenso/goose/blob/main/LICENSE) for licensing details.

