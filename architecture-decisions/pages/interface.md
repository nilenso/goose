ADR: Interface
=============

Goose's interface:
```clojure
(goose.client/perform-async goose.client/default-opts `my-background-fn :arg1 "arg2"...)
(let [worker (goose.worker/start goose.worker/default-opts)]
  (goose.worker/stop worker))
```

Rationale
---------

##### Client
- `opts` need to be explicitly set because everything is transparent. No config will be set implicitly without user's knowledge or control 
- Goose will not implicitly pick config from env var, users can use default opts, or choose set opts.
- Interface isn't finalized yet. It'll be decided as part of [this issue](https://github.com/nilenso/goose/issues/36)

##### Worker
- Stopping a worker is a `reify` because contexts local to a process (ex: `id`) are used during shutdown & must not be lost

Avoided Designs
---------
- `s-expression`
  - if someone can help change Goose's interface to this: `(async opts (bg-fn :arg1 :arg2)` from ``(async opts `bg-fn :arg1 :arg2)`` we'll be very thankful.
  - [at-at](https://github.com/overtone/at-at), [chime](https://github.com/jarohen/chime), etc. have this interface because they're in-memory. They don't have to serialize the s-expression & can evaluate them at a later time.
- wrap async functions inside a macro
  - It felt OOish, not functional
- Clojure protocol for Goose
  - No extra benefits, more complicated implementation
