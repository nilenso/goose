ADR: Interface
=============

Goose's interface:
```clojure
(c/perform-async client-opts `my-background-fn :arg1 "arg2"...)
(let [worker (w/start worker-opts)]
  (w/stop worker))
```

Rationale
---------

- Args to background-function are kept last as they're variadic
- Goose requires application to store state of a broker & inject it
  - Since we're reusing rmq/redis connection pools, broker state must be stored somewhere
  - DI approach was chosen over injecting config & memoizing connection state based on that
  - If Goose stores state in an atom/memoizes it & has a 1-to-1 mapping with config, that's non-intuitive & confusing
  - Carmine also derives connection state from config passed into it & that has led to confusion many-a-times
  - For this reason, we've designed it so that state of broker is injected by application, not stored internally by Goose
- Goose doesn't endorse a particular broker. Users have to choose between RabbitMQ, Redis or implement their own message broker

##### Client

- `opts` need to be explicitly set because everything is transparent. No config will be set implicitly without user's knowledge or control
- Goose will not implicitly pick config from env var, users can use default opts, or set their own config

##### Worker

- Stopping a worker is a `reify` because contexts local to a process (ex: `id`) are used during shutdown & must not be lost

Avoided Designs
---------

- `s-expression`
  - Since anything can be passed as an s-expression, serializing at runtime isn't possible.
  - [at-at](https://github.com/overtone/at-at), [chime](https://github.com/jarohen/chime), etc. have this interface because they're in-memory. They don't have to serialize the s-expression & can evaluate them at a later time.
  - We'd want an interface like this: `(perform-async opts (bg-fn :arg1 :arg2)` from ``(perform-async opts `bg-fn :arg1 :arg2)``, but that isn't possible with the constructs of Clojure
- Predefine background functions & their options as data in a common map accessible to both client & worker
  - It felt OOish, not functional
  - Duplication: if you stop enqueuing a function, you've to remember to remove it from the map too.
  - No significant performance improvement found over chosen interface as demonstrated in [this issue](https://github.com/nilenso/goose/issues/36)
  
- wrap async functions inside a macro
  - It felt OOish, not functional
- Clojure protocol for Goose
  - No extra benefits, more complicated implementation
