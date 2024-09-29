Welcome to Goose contributing guide
========

Thank you for investing your time in contributing to our project!

Before you start contributing, we're presuming you've gone through all the [architecture-decisions](https://github.com/nilenso/goose/tree/main/architecture-decisions) & had a discussion with the maintainers.

Installation
--------

- [Clojure v1.11+](https://clojure.org/guides/install_clojure)
- Infra dependencies (note: for easy testing, use the docker compose manifest)
  - [Redis v6.2.0+](https://redis.io/docs/getting-started/installation/)
  - [RabbitMQ v3.9.0+](https://www.rabbitmq.com/download.html)
    - [Management Plugin](https://www.rabbitmq.com/management.html#getting-started)
    - [RabbitMQ Delayed Message Plugin](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange)
- Use `:repl` profile when starting REPL in your IDE

Testing
--------

The test suite is mostly integration tests, so use docker-compose to get the containerized Redis and RabbitMQ images for testing purposes.

```shell
$ docker-compose up -d                   # Sets up infra required for tests.
$ clj -X:test :nses '[goose.specs-test]' # Testing only 1 namespace.
$ clj -X:test                            # Running all tests.
$ docker-compose down                    # ...when you're done.
```

Formatting
--------
- Goose adheres to the [weavejester/cljfmt](https://clojars.org/dev.weavejester/cljfmt) library to maintain consistent code formatting.
- Install the appropriate plugin for your IDE to ensure compliance.

```shell
$ clj -M:cljfmt fix                      # Automatically fix formatting issues.
$ clj -M:cljfmt check                    # Check any formatting inconsistencies.
```

Linting
--------
- Install [clj-kondo v2022.10.05](https://github.com/clj-kondo/clj-kondo/blob/master/doc/install.md#installation-script-macos-and-linux)

```shell
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

Documentation Guidelines
--------

- Every new feature should have an ADR, well-formatted docstrings & a Wiki Page.
- ADRs (Architecture Decision Records) should be added to [this folder](https://github.com/nilenso/goose/tree/main/architecture-decisions).
- Docstrings serve 3 purposes:
  1. Record the _How and What_ of an external function
  1. Document usage of a function and meaning of its args
  1. Beautify documentation of Goose APIs on [cljdoc](https://cljdoc.org/d/com.nilenso/goose/), a documentation website for Clojure community
- When writing docstrings, follow these guidelines:
  - Put code logic/reasoning in code-comments & only cover usage of a function in docstrings.
  - Format docstrings in markdown for parsing by Cljdoc.
  - Capitalize the first line.
  - Use double backslashes (`\\`) for line break when a new line isn't feasible.
  - Wrap positional args with backtick (example: `:broker`).
  - Link to other functions/data in Goose using [API Wikilinks syntax](https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc#use-api-wikilinks-from-docstrings) (example: `[[goose.retry/default-opts]]`).
    - In API Wikilinks, use fully-qualified namespaces and avoid alias or relative paths for namespaces.

Making a Release
--------

After feature-completion or bug-fix, we release a new version by marking a tag on Github, and publishing the new package to Clojars. Follow below steps when making a release:

1. Compare code-changes between previous release and HEAD branch main by [following this link](https://github.com/nilenso/goose/compare/0.4.0...main).
1. Choose a new release version based on [semantic versioning](https://semver.org/) guidelines.
1. Commit new release version to [README.md](./README.md), [CHANGELOG](./CHANGELOG.md) and [pom.xml](./pom.xml).
1. Draft a new release by going to [this link](https://github.com/nilenso/goose/releases/new)
1. While choosing a tag, enter the release number to be created on publish.
1. Click on `Generate release notes`, and review the generated notes.
1. Click on `Publish release`, and [track the status of new package publishing in CI](https://github.com/nilenso/goose/actions/workflows/publish.yml).
1. Verify Goose is released on [Clojars](https://clojars.org/com.nilenso/goose) and docs are updated on [cljdoc.org](https://cljdoc.org/d/com.nilenso/goose/).
