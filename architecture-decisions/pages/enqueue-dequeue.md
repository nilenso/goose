ADR: Enqueue-Dequeue 
=============

Rationale
---------

- Goose uses [taoensso/nippy](https://github.com/ptaoussanis/nippy) for serialization because it is fastest & extensible
  - Nippy can be used to serialize custom user-defined defrecords too

### RabbitMQ
- In RabbitMQ, state of a connection & channels is maintained
  - Goose doesn't open/close channels every now-and-then. Instead, it maintains a pool of channels
  - A pitfall in creating new channels on every publish is the risk of breaching max-channels limit if throughput is too high
- Queues have following properties: `durable: true`, `auto-delete: false`,  `exclusive: false` and `x-max-priority: 1`
- All messages have following properties: `persistent: true`, `priority: 0`
  - Jobs scheduled to run in past will be enqueued to front of queue with `priority: 9`
- Goose doesn't define an exchange, and uses RabbitMQ's default one instead
- Usage of channel-pooling
  - It is inefficient to open & close a channel for usage. Hence, Goose maintains a pool of channels to publish/subscribe
  - Users have flexibility to define channel-pool size on client-side based on rate of publishing
  - On worker-side, channel-pool size is equal to thread-pool size
- For reliability, acknowledgements are sent after a job is executed successfully
  - For this reason, a prefetch-count of 1 is set on every channel
- We inject [custom thread-pools when dequeuing & executing jobs](https://www.rabbitmq.com/api-guide.html#consumer-thread-pool)
- RabbitMQ uses return-listener to get notified of dropped messages
- `timestamp` isn't added to msg properties, instead put inside job for reusability of job/calculate-latency
  - Scheduling & Retry latency cannot be calculated by message timestamp
- Publisher confirm strategy:
  - sync is good enough for 90% applications (200 msg/sec)
  - However, async can be used for high-scale (8000 msg/sec: a 40x performance benefit)
  - When doing async-publishing, Goose locks a channel to avoid race-conditions with `getNextPublishSeqNo`
- Not using [transactions](https://www.rabbitmq.com/semantics.html#tx)
  - Overall the behaviour of the AMQP tx class, and more so its implementation on RabbitMQ, is closer to providing a 'batching' feature than ACID capabilities known from the database world
  - Since Goose doesn't have batching, using transactions doesn't fit any use-case

### Redis
- Using a redis list because enqueue-dequeue operations are O(1)
- Using `BRPOPLPUSH` because of reliability

Avoided Designs
---------
- During execution of a job, `resolve` isn't memoized because hot-reloading becomes a pain
