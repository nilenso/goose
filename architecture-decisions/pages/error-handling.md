ADR: Error-handling & Retrying
=============

Rationale
---------
- Reuse same schedule queue for retrying jobs
    - Semantics of `scheduled jobs` & `failed jobs due for retry` are same
    - Half load on broker
    - No duplication in code, even at abstraction level
- User can specify retry-queue
  - To reduce or increase priority of jobs due for retry
- Error & Death handlers
    - Give users the flexibility to inject their own error-service or custom code
- Storing just error message, and not entire stacktrace. If users want to get a hold of entire stack trace, they can do so using error/death handlers or middlewares

### RabbitMQ
- We aren't using DLQ for error-handling because of following reasons:
  - In Redis, we store retry-count in Job & schedule it with appropriate back-off
  - Goose does the heavy lifting here ^^
  - Dead-Letter Exchange that claims to do the heavy lifting, but won't work for Goose's use-case
  - With DLX, a retry-flow would look like this:
    1. Job is published to `my-queue`
    1. Job execution fails
    1. Job is enqueued to `retry-queue` with TTL of 2 mins
    1. When Job expires, it goes from `retry-queue` to DLX
    1. DLX routes the job to the back to `my-queue` for execution
  - Here, a MAJOR flaw is that RabbitMQ pops expired messages only from head of the queue. You cannot use a single wait queue for any back-off strategy. If you have a message with a TTL of 10 minutes at the head of the queue and a message with a TTL of 1 minute behind it, the second message will wait for ten minutes. [Details in RMQ blog](https://www.rabbitmq.com/ttl.html#per-message-ttl-caveats)
  - For ^^ reason, we chose delayed-message plugin for scheduling, and avoided per-message TTL
- Not using `x-death` header because state is stored inside the job itself
  - To send a msg to dead-jobs queue, there are 2 options:
    1. `lb/nack` the msg, gets routed to dead-letter exchange, which is fanout & sends it to dead-jobs queue
    1. manually enqueue it to dead-jobs queue
  - We chose to do it manually because:
    - If user wants to `skip-dead-queue`, that couldn't be done with lb/nack
    - We loose goose-specific data such as `:died-at`, `:last-retried-at`

Avoided Designs
---------

- Different queue to schedule jobs due for retry
    - It increases load on broker
