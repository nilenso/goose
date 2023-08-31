ADR: API
=============

Rationale
---------

### RabbitMQ

- Lots of API aren't implemented because they aren't possible using the semantics of RMQ
- Shovel won't help with moving jobs from & there's no concept of transaction, so data-loss is a probability
- No list-all-queues for RMQ. Anything that needs to be done over an API can be done via management portal.
- When building for RMQ API, broker initialization gets unnecessary complications. Hence, it is avoided
