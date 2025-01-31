Goose
=========
<p align="center">
  <img src="logo/goose-round@2x.png" title="Goose" width="360" height="360" />
</p>

[![Test & Lint Workflow](https://github.com/nilenso/goose/actions/workflows/fmt_test_lint.yml/badge.svg)](https://github.com/nilenso/goose/actions/workflows/fmt_test_lint.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.nilenso/goose.svg)](https://clojars.org/com.nilenso/goose)
[![cljdoc badge](https://cljdoc.org/badge/com.nilenso/goose)](https://cljdoc.org/d/com.nilenso/goose)

### Simple. Pluggable. Reliable. Extensible. Scalable.
A powerful background job processing library for Clojure. Goose is named after [LT Nick 'Goose' Bradshaw](https://topgun.fandom.com/wiki/Nick_Bradshaw), the _sidekick_ to [Captain Pete 'Maverick' Mitchell](https://topgun.fandom.com/wiki/Pete_Mitchell) in Top Gun.

Announcement 🔈
---------
We are excited to announce that **five companies** are successfully and reliably using Goose in production environments. With the release of a [Jobs Management Console](https://github.com/nilenso/goose/wiki/Console), Goose has reached a level of maturity, offering a feature-rich, stable, and production-ready background job processing solution.

As we continue addressing user feedback, we are *rescheduling* the **1.0.0 release to February 1st, 2026**. In the meantime, we will ensure API stability, backward compatibility and zero-downtime upgrades.

We welcome feedback from current and new users, especially if you’d like to request features or suggest changes to the API or implementation before the 1.0.0 release. Please review our [Architecture Decisions](https://github.com/nilenso/goose/tree/main/architecture-decisions) and [Wiki](https://github.com/nilenso/goose/wiki) for more context, and share your thoughts via [GitHub Issues](https://github.com/nilenso/goose/issues/new) or the [Clojurians Slack](https://clojurians.slack.com/channels/goose).

Features
---------
- *Reliable* - Code/Hardware/Network failure won't cause data loss
- Native support for RabbitMQ & Redis queues
- Pluggable [Message Broker](https://github.com/nilenso/goose/wiki/Guide-to-Message-Broker-Integration) & [Metrics Backend](https://github.com/nilenso/goose/wiki/Guide-to-Custom-Metrics-Backend)
- [Jobs management Console](https://github.com/nilenso/goose/wiki/Console)
- [Scheduled Jobs](https://github.com/nilenso/goose/wiki/Scheduled-Jobs)
- [Batch Jobs](https://github.com/nilenso/goose/wiki/Batch-Jobs)
- [Cron Jobs](https://github.com/nilenso/goose/wiki/Cron-Jobs)
- [Error Handling & Retries](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries)
- Extensible using [Middlewares](https://github.com/nilenso/goose/wiki/Middlewares)
- Performant and scalable (refer [performance benchmarks](https://github.com/nilenso/goose/tree/main/perf))
- Concurrency & Parallelism friendly
- ... more details in [Goose Wiki](https://github.com/nilenso/goose/wiki)

Companies using Goose in Production
---------
<a href="https://aspect-analytics.com/">
  <img src="logo/aspect-analytics.png" title="Aspect Analytics" width="150" height="150" />
</a>
<a href="https://beecastle.com/">
  <img src="logo/beecastle.png" title="BeeCastle" width="150" height="150" />
</a>
<a href="https://consolidate.health/">
  <img src="logo/consolidate-health.png" title="Consolidate Health" width="150" height="150" />
</a>

Getting Started
---------
[![Clojars Project](https://img.shields.io/clojars/v/com.nilenso/goose.svg?labelColor=283C67&color=729AD1&style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/com.nilenso/goose)

### Add Goose as a dependency
```Clojure
;;; Clojure CLI/deps.edn
com.nilenso/goose {:mvn/version "0.6.0"}

;;; Leiningen/Boot
[com.nilenso/goose "0.6.0"]
```

### Client
```Clojure
(ns my-app
  (:require
    [goose.brokers.rmq.broker :as rmq]
    [goose.client :as c]))

(defn my-fn
  [arg1 arg2]
  (println "my-fn called with" arg1 arg2))

(let [rmq-producer (rmq/new-producer rmq/default-opts)
      ;; Along with RabbitMQ, Goose supports Redis as well.
      client-opts (assoc c/default-opts :broker rmq-producer)]
  ;; Supply a fully-qualified function symbol for enqueuing.
  ;; Args to perform-async are variadic.
  (c/perform-async client-opts `my-fn "foo" :bar)
  (c/perform-in-sec client-opts 900 `my-fn "foo" :bar)
  ;; When shutting down client...
  (rmq/close rmq-producer))
```

### Worker
```Clojure
(ns my-worker
  (:require
    [goose.brokers.rmq.broker :as rmq]
    [goose.worker :as w]))

;;; 'my-app' namespace should be resolvable by worker.
(let [rmq-consumer (rmq/new-consumer rmq/default-opts)
      ;; Along with RabbitMQ, Goose supports Redis as well.
      worker-opts (assoc w/default-opts :broker rmq-consumer)
      worker (w/start worker-opts)]
  ;; When shutting down worker...
  (w/stop worker) ; Performs a graceful shutsdown.
  (rmq/close rmq-consumer))
```
Refer to wiki for [Redis queue](https://github.com/nilenso/goose/wiki/Redis), [Jobs management Console](https://github.com/nilenso/goose/wiki/Console), [Batch Jobs](https://github.com/nilenso/goose/wiki/Batch-Jobs), [Cron Jobs](https://github.com/nilenso/goose/wiki/Cron-Jobs), [Error Handling](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries), [Monitoring](https://github.com/nilenso/goose/wiki/Monitoring-&-Alerting), [Production Readiness](https://github.com/nilenso/goose/wiki/Production-Readiness) and more.

Getting Help
---------
[![Get help on Slack](http://img.shields.io/badge/slack-clojurians%20%23goose-F49109?labelColor=3c0c3c&logo=slack&style=for-the-badge)](https://clojurians.slack.com/channels/goose)

Please [open an issue](https://github.com/nilenso/goose/issues/new) or ping us on [#goose @Clojurians slack](https://clojurians.slack.com/channels/goose).

License
---------
[![Licence](https://img.shields.io/github/license/Ileriayo/markdown-badges?style=for-the-badge)](./LICENSE)

Contributing
---------
- As a first step, go through all the [architecture-decisions](https://github.com/nilenso/goose/tree/main/architecture-decisions)
- Discuss with maintainers on the [issues page](https://github.com/nilenso/goose/issues) or at [#goose @Clojurians slack](https://clojurians.slack.com/channels/goose)
- See [the contributing guide](https://github.com/nilenso/goose/blob/main/CONTRIBUTING.md) for setup & guidelines
