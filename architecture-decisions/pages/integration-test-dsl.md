ADR: Integration Test DSL
=========================

Goose uses a small integration test DSL to express broker-agnostic behavior once and execute it across all registered broker implementations.

The DSL lives under `test/goose/integration` and is intended for Goose's own integration tests, not as a public user-facing API.

Context
-------

Historically, Redis and RabbitMQ integration tests duplicated the same semantic scenarios in broker-specific namespaces. This made it harder to:

- compare broker behavior consistently
- add a new broker implementation
- know which parts of the broker protocol a test scenario requires
- prune or evolve old broker-specific integration tests safely

The DSL was introduced in [#214](https://github.com/nilenso/goose/pull/214) and is being used for the migration tracked in [#216](https://github.com/nilenso/goose/issues/216). The broader direction is tracked in [#202](https://github.com/nilenso/goose/issues/202).

Decision
--------

Common broker behavior should be written using `goose.integration.utils/def-integration-test`.

A DSL test declares:

1. a logical test name
2. the broker protocol capabilities required by the test
3. a test body that is executed once per registered broker that satisfies those requirements

Example:

```clojure
(def-integration-test async-execution-test
  #{:enqueue}
  (let [job (c/perform-async (u/get-opts broker :client)
                             `executable
                             test-name
                             ::async-execution-test)
        worker (w/start (u/get-opts broker :worker))]
    (is (uuid? (UUID/fromString (:id job))))
    (is (= ::async-execution-test
           (delivered-execution test-name)))
    (w/stop worker)))
```

The macro exposes two local anaphoras in the test body:

- `broker`: the current broker keyword, e.g. `:redis` or `:rabbitmq`
- `test-name`: a broker-qualified logical test name, e.g. `"redis-async-execution-test"`

Broker-specific opts should be accessed through:

```clojure
(u/get-opts broker :client)
(u/get-opts broker :worker)
```

The test body should not hard-code `tu/redis-client-opts`, `tu/rmq-client-opts`, etc. unless the test is intentionally broker-specific and should stay outside the DSL suite.

Broker Registry
---------------

Broker test runtime data is registered in `goose.integration.utils/broker-utils`:

```clojure
{:commons {:execution-timeout-ms 3000}
 :implementations
 {:redis    {:fixture redis-fixture-fn
             :opts {:client redis-client-opts
                    :worker redis-worker-opts}}
  :rabbitmq {:fixture rabbitmq-fixture-fn
             :opts {:client rabbitmq-client-opts
                    :worker rabbitmq-worker-opts}}}}
```

Each broker entry provides:

- a fixture function for setup/cleanup
- client opts
- worker opts

Adding a new broker to the common integration suite should primarily mean registering equivalent test utilities here and registering the implementation class with capability evaluation.

Capability Dispatch
-------------------

`test/goose/capability.clj` reflects on broker implementation classes and compares their concrete methods with `goose.broker/Broker` protocol methods.

The DSL test requirements are sets of protocol-method keywords:

```clojure
#{:enqueue}
#{:schedule}
#{:enqueue :schedule}
```

Before running a test body for a broker, the DSL checks whether the broker implements the required capability set. If not, the test reports that the broker is not testable for that scenario.

These method-level capabilities are an internal test-dispatch mechanism. They should not necessarily be treated as the desired user-facing documentation taxonomy; see [#208](https://github.com/nilenso/goose/issues/208).

Execution Delivery Semantics
----------------------------

The DSL provides promise-backed helpers to avoid sleeps and ad-hoc polling in tests.

Core helpers:

```clojure
(setup-test-promise test-name)
(executable test-name value)
(delivered-execution test-name)
(delivered? test-name)
```

`def-integration-test` sets up a delivery promise for the base `test-name` before running each broker case. Tests can enqueue `executable` as the job function and assert on the delivered value:

```clojure
(c/perform-async (u/get-opts broker :client)
                 `executable
                 test-name
                 ::some-result)

(is (= ::some-result
       (delivered-execution test-name)))
```

For tests with multiple events, derive event-specific delivery names from `test-name` and set them up explicitly:

```clojure
(defn delivery-name [test-name event]
  (str test-name "-" (name event)))

(defn setup-deliveries [test-name events]
  (doseq [event events]
    (u/setup-test-promise (delivery-name test-name event))))
```

This pattern is useful for retry/death tests that need to observe multiple callbacks or attempts without introducing separate global promise atoms.

What Belongs in the DSL Suite?
------------------------------

Use the DSL when the test asserts semantic behavior that should hold for every broker implementation with the required capabilities.

Currently migrated examples include:

- async execution
- absolute scheduling
- relative scheduling
- middleware execution
- retry flow with a custom retry queue
- death handling after retry exhaustion

Keep tests in broker-specific namespaces when they assert implementation-specific behavior. Examples:

- Redis orphan job recovery
- Redis batch behavior
- RabbitMQ publisher confirms
- RabbitMQ quorum queues
- RabbitMQ ACK behavior
- RabbitMQ metadata in middleware opts
- RabbitMQ graceful shutdown
- RabbitMQ max-delay scheduling guard

Rationale
---------

This design keeps common broker behavior in one place while still allowing broker-specific tests where implementation details matter.

Benefits:

- less duplicated test code across broker implementations
- clearer signal about which broker protocol methods a scenario requires
- easier onboarding path for future broker implementations
- less flaky execution assertions by preferring promise delivery over sleeps
- broker setup/cleanup remains centralized in fixtures

Tradeoffs
---------

- Tests using multiple delivery events need derived delivery names, which can be less direct than local promise atoms.
- Requirements are currently protocol-method-level and therefore more granular than the conceptual capability a developer may have in mind.
- The `broker` and `test-name` anaphoras are intentionally concise but should be documented and used consistently.

Consequences
------------

When porting an existing broker-specific integration test:

1. Decide whether the behavior is truly common across brokers.
2. If common, move it to `test/goose/integration/goose_test.clj` using `def-integration-test`.
3. Replace hard-coded broker opts with `(u/get-opts broker :client)` and `(u/get-opts broker :worker)`.
4. Declare the smallest capability set required by the scenario.
5. Prefer `executable`, `setup-test-promise`, and `delivered-execution` over custom sleeps or per-test promise atoms.
6. Prune the old broker-specific test once equivalent common coverage exists.

Tests can be run with:

```bash
bb int
```

or directly with:

```bash
clj -X:test :dirs '["test/goose/integration"]'
```
