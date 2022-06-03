Goose: The sidekick for Clojure
=========

Simple, Scalable background processing library for Clojure.

Features
---------

*[Simplicity is Complicated.](https://youtu.be/rFejpH_tAHM)* We've strived to make Goose as simple, transparent & functional as possible.

- A *simple*, *functional* interface
- *Reliable* - Code/Hardware/Network failure won't cause data loss
- [Cloud-Native Architecture](link-to-architecture-decisions-dir)
- Lean, Expressive, Transparent & Extensible
- Plug-and-play, minimal setup & *sane defaults*
- Concurrency & Parallelism using Java thread-pools
- Unit & E2E Tests 🙂

Getting Started
---------

**Note:** All configurations are optional. Goose defaults to values mentioned in Configuration Options.

### Client

### Worker

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

