Goose: The sidekick for Clojure
=========

Simple, Reliable & Scalable background processing library for Clojure.

Performance
---------

Details can be found in the [Benchmarking section](https://github.com/nilenso/goose/tree/main/perf).

Features
---------

*[Simplicity is Complicated.](https://youtu.be/rFejpH_tAHM)* We've strived to make Goose as _simple_ as possible.

- A *functional* interface
- *Reliable* - Code/Hardware/Network failure won't cause data loss
- [Cloud-Native Architecture](https://github.com/nilenso/goose/tree/main/architecture-decisions)
- Lean, Expressive, Transparent & Extensible
- Plug-and-play, minimal setup with *sane defaults*
- Concurrency & Parallelism using Java thread-pools
- Unit, Integration & [Performance](https://github.com/nilenso/goose/tree/main/perf) Tests 🙂

Getting Started
---------

[![Clojars Project](https://img.shields.io/clojars/v/com.nilenso/goose.svg?labelColor=283C67&color=729AD1&style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/com.nilenso/goose)

*Note:* Goose will be ready for production usage after completion of [Project 0.2](https://github.com/orgs/nilenso/projects/1/)

### Client

```clojure
(ns my-app
  (:require
    [goose.client :as c]
    [goose.brokers.redis :as redis]))

(defn my-background-fn
  [arg1 arg2]
  (println "my-background-fn called with" arg1 arg2))

(def client-opts
  (assoc c/default-opts
    :broker-opts {:redis redis/default-opts}))
(c/perform-async client-opts `my-background-fn "foo" :bar)
(c/perform-in-sec client-opts 3600 `my-background-fn "scheduled" 123)
```

### Worker

```clojure
(ns my-worker
  (:require [goose.worker :as w]))

(let [worker-opts (assoc w/default-opts
                    :broker-opts {:redis redis/default-opts})
      worker (w/start worker-opts)]
  ; ... wait for SIGINT or SIGTERM ...
  (w/stop worker))
```

Custom Configuration
---------

Goose provisions custom configuration for Message-Broker, Priority Queues, Scheduling, Error-Handling & Retrying, Logging, & Worker Config.
Details can be found in the respective Wikis ([awaiting completion](https://github.com/nilenso/goose/issues/40)).

Why the name "Goose"?
---------

> 🦆 Logo loading...

Goose library is named after [Nick 'Goose' Bradshaw](https://historica.fandom.com/wiki/Nick_Bradshaw), the sidekick
to [Captain Pete 'Maverick' Mitchell](https://topgun.fandom.com/wiki/Pete_Mitchell) in Top Gun.

License
---------

Please see [LICENSE](https://github.com/nilenso/goose/blob/main/LICENSE) for licensing details.

