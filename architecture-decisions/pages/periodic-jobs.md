ADR: Periodic Jobs
=============

Rationale
---------

### Redis
- Implementation details:
  - `:cron-name` is the unique identifier for a Periodic Job
  - We've separated Job description from the actual Job itself
  - Goose stores the description in a hash-set by the key of it's `:cron-name`
  - After storing it in hash-set, Goose creates an entry in a sorted-set with score of next runtime
  - When a job is due, Goose creates a Job from the description & enqueue it to ready-queue
  - Add another entry in sorted-set for next runtime
- Limitations:
  - The most frequently a job can be run periodically is every minute
  - Backfill: In case workers weren't running, Goose will backfill only 1 cron job, instead of n missed jobs
- There's no expiry on Periodic Jobs, hence no need for renewal/re-registration. Until the fixed count feature is implemented, cron job can be stopped/deleted only using API.
- Versioning using enqueue timestamp is a good-enough feature & doesn't need a dedicated versioning stamp

### RabbitMQ

- Periodic jobs feature cannot be implemented in RabbitMQ because of:
  - Lack of a persistent SET/GET data structure
  - Lack of querying/updating/deleting jobs inside a queue
- Unique registration can be done using `noxdafox/rabbitmq-message-deduplication` plugin
  - However, deleting a job isn't possible
- If RMQ had a way to set/get data, that would make periodic jobs possible
