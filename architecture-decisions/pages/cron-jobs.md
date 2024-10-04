ADR: Cron Jobs
=============

Rationale
---------

### Nomenclature

While "Periodic Jobs" is technically a more accurate term for jobs that execute recurringly at fixed intervals (as reflected in dictionary definitions of "periodic" versus "cron"), we opted to use the term **Cron jobs** for its familiarity and conciseness. This decision prioritizes ease of understanding for the broader developer community.

### Redis
- Implementation details:
  - `:cron-name` is the unique identifier for a Cron Job
  - We've separated Job description from the actual Job itself
  - Goose stores the description in a hash-set by the key of its `:cron-name`
  - After storing it in hash-set, Goose creates an entry in a sorted-set with score of next runtime
  - When a job is due, Goose creates a Job from the description & enqueue it to ready-queue
  - Add another entry in sorted-set for next runtime
- Limitations:
  - The most frequently a job can be run recurringly is every minute
  - Backfill: In case workers weren't running, Goose will backfill only 1 cron job, instead of n missed jobs
- There's no expiry on Cron Jobs, hence no need for renewal/re-registration. Until the fixed count feature is implemented, a cron job can be stopped/deleted only using API.
- Versioning using enqueue timestamp is a good-enough feature & doesn't need a dedicated versioning stamp

### RabbitMQ

- Cron jobs feature cannot be implemented in RabbitMQ because of:
  - Lack of a persistent SET/GET data structure
  - Lack of querying/updating/deleting jobs inside a queue
- Unique registration can be done using `noxdafox/rabbitmq-message-deduplication` plugin
  - However, deleting a job isn't possible
- If RMQ had a way to set/get data, that would make implementing cron jobs possible
