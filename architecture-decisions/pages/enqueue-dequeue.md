ADR: Enqueue-Dequeue 
=============

Rationale
---------

- Goose uses [taoensso/nippy](https://github.com/ptaoussanis/nippy) for serialization because it is fastest & extensible
  - Nippy can be used to serialize custom user-defined defrecords too

### Redis
- Using a redis list because enqueue-dequeue operations are O(1)
- Using `BRPOPLPUSH` because of reliability

### RabbitMQ
- In RabbitMQ, state of a connection & channels is maintained
- Queues have following properties: `durable: true`, `auto-delete: false` and `exclusive: false`
- All messages have following properties: `persistent: true`, `priority: 0`
  - Jobs scheduled to run in past will be enqueued to front of queue with `priority: 9`
- Goose doesn't define an exchange, and uses RabbitMQ's default one instead
- Usage of channel-pooling
  - It is inefficient to open & close a channel for usage. Hence, Goose maintains a pool of channels to publish/subscribe
  - Users have flexibility to define channel-pool size on client-side based on rate of publishing
  - On worker-side, channel-pool size is equal to thread-pool size
- When a channel gets [closed due to client/server-side errors](https://www.rabbitmq.com/channels.html#error-handling), Goose won't reopen them under-the-hood. Users will have to do so when they catch such an exception
  - The probability of channel getting closed is very low though
- For reliability, acknowledgements are sent after a job is executed successfully
  - For this reason, a prefetch-count of 1 is set on every channel
- Usage of thread-pools 
  - In Redis, thread-pool is used to both listen & execute a job
  - In RabbitMQ, `langohr` has async mechanism for receiving messages from the queue
  - `thread-pool` is used only for execution, and not to listen for new jobs

Avoided Designs
---------
- During execution of a job, `resolve` isn't memoized because hot-reloading becomes a pain
