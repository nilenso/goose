Goose 🦆
=========
[![Test & Lint Workflow](https://github.com/nilenso/goose/actions/workflows/test_lint.yml/badge.svg)](https://github.com/nilenso/goose/actions/workflows/test_lint.yml)
[![cljdoc badge](https://cljdoc.org/badge/com.nilenso/goose)](https://cljdoc.org/d/com.nilenso/goose)

A *Simple, Reliable & Scalable* background job processing library for Clojure.

Performance
---------
Please refer to the [Benchmarking section](https://github.com/nilenso/goose/tree/main/perf).

Features
---------
- *Reliable* - Code/Hardware/Network failure won't cause data loss
- [Transparent Design & Cloud-Native Architecture](https://github.com/nilenso/goose/tree/main/architecture-decisions)
- [Scheduling](https://github.com/nilenso/goose/wiki/Scheduling)
- [Error Handling & Retries](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries)
- Concurrency & Parallelism using Java thread-pools
- Unit, Integration & [Performance](https://github.com/nilenso/goose/tree/main/perf) Tests

Getting Started
---------

[![Clojars Project](https://img.shields.io/clojars/v/com.nilenso/goose.svg?labelColor=283C67&color=729AD1&style=for-the-badge&logo=clojure&logoColor=fff)](https://clojars.org/com.nilenso/goose)

For details, refer to [Goose Wiki](https://github.com/nilenso/goose/wiki).
### Client

```clojure
(ns my-app
  (:require [goose.client :as c]))

(defn my-fn
  [arg1 arg2]
  (println "my-fn called with" arg1 arg2))

; Supply a fully-qualified function symbol for enqueuing.
; Args to perform-async are variadic.
(c/perform-async c/default-opts `my-fn "foo" :bar)
```

### Worker

```clojure
(ns my-worker
  (:require [goose.worker :as w]))

; my-app namespace should be resolvable.
(let [worker (w/start w/default-opts)]
  ; ... listen for SIGINT or SIGTERM ...
  (w/stop worker))
```

Getting Help
---------
[![Get help on Slack](http://img.shields.io/badge/slack-clojurians%20%23goose-F49109?labelColor=3c0c3c&logo=slack&style=for-the-badge)](https://clojurians.slack.com/channels/goose)

Please [open an issue](https://github.com/nilenso/goose/issues/new) or ping us on [#goose @Clojurians slack](https://clojurians.slack.com/channels/goose).

Why the name "Goose"?
---------
<p align="center">
  <img src="logo/goose-round@2x.png" width="360">
</p>

Named after [Nick 'Goose' Bradshaw](https://historica.fandom.com/wiki/Nick_Bradshaw), the _sidekick_
to [Captain Pete 'Maverick' Mitchell](https://topgun.fandom.com/wiki/Pete_Mitchell) in Top Gun.

License
---------
[![Licence](https://img.shields.io/github/license/Ileriayo/markdown-badges?style=for-the-badge)](./LICENSE)
