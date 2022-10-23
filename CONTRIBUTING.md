Welcome to Goose contributing guide
========

Thank you for investing your time in contributing to our project!

Before you start contributing, we're presuming you've gone through all the [architecture-decisions](https://github.com/nilenso/goose/tree/main/architecture-decisions) & had a discussion with the maintainers.

Installation
--------

- [Clojure v1.11+](https://clojure.org/guides/install_clojure)
- [Redis v6.2.0+](https://redis.io/docs/getting-started/installation/)
- [RabbitMQ v3.9.0+](https://www.rabbitmq.com/download.html)
  - [Management Plugin](https://www.rabbitmq.com/management.html#getting-started)
  - [RabbitMQ Delayed Message Plugin](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange)
- Use `:repl` profile when starting REPL in your IDE

Testing & Linting
--------

- Install [clj-kondo](https://github.com/clj-kondo/clj-kondo/blob/master/doc/install.md#installation-script-macos-and-linux)
```shell
$ clj -X:test :nses '[goose.specs-test]' # Testing only 1 namespace.
$ clj -X:test                            # Running all tests.
$ clj-kondo --lint src                   # Linting src dir.
$ clj-kondo --lint test                  # Linting test dir.
```

Gotchas
--------

- Goose uses `epoch` in milliseconds

Coding Guidelines
--------

These aren't exhaustive, but majorly inspired from [The Clojure Style Guide](https://guide.clojure.style)

- `defn`
  - Add args in same line if function is a 1-liner
  - Add args after a linebreak if function has a docstring or is longer than 1 line, 
- [Use Idiomatic Namespace Aliases](https://guide.clojure.style/#use-idiomatic-namespace-aliases)
- [Prefer anonymous functions over partial](https://guide.clojure.style/#anonymous-functions-vs-complement-comp-and-partial)
- In specs, use `set` as a predicate for equality/contains functionality
- Use compact metadata notation for flags
- Follow [comments](https://guide.clojure.style/#comments) & [docstrings](https://guide.clojure.style/#documentation) guidelines
