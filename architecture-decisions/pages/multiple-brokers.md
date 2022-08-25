ADR: Multiple Brokers
=============

Features & Approach
-------------

| Feature | Redis | RabbitMQ | Amazon SQS |
| --- | --- | --- | --- |
| **Enqueue** | `LPUSH` | `QUEUE(durable: true)`<br>`PUBLISH(persistent: true)` | `QUEUE(standard)`<sup>1</sup><br>`SEND` |
| **Dequeue** | `BRPOPLPUSH`<br>*execute*<br>`LREM` | `SUBSCRIBE(manual_ack: true)`<br>*execute*<br>`channel.ACK`<br>[reference](https://www.rabbitmq.com/tutorials/tutorial-two-ruby.html) | `RECEIVE(with visibility timeouts)`<br>*execute*<br>`DELETE`<br>[reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) |
| **Scheduled /**<br>**Periodic Jobs** | `ZADD`<br>`ZRANGEBYSCORE`<br>`ZREM`<br>*Priority Enqueue* | `PUBLISH(x-delay: 123ms)`<br>*Dequeue*<br>[reference](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange) | `SEND(delay: 123s)`<br>`RECEIVE`<br>*Dequeue*<br>**or**<br>*Re-Delay (because max delay is 15 minutes)*<br>[reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html) |
| **Failed Jobs /**<br>**Retries** | *Schedule with*<br>*retry delay* | `RETRY-EXCHANGE`<br>*Re-Enqeueue with delay*<br>[reference](https://dzone.com/articles/rabbitmq-consumer-retry-mechanism) | *Schedule with*<br>*retry delay*<br>[reference](https://docs.aws.amazon.com/lambda/latest/operatorguide/sqs-retries.html) |
| **Dead Jobs** | *Add to*<br>*dead-queue* | *Use Dead-Letter Exchanges*<br>[reference](https://www.rabbitmq.com/dlx.html) | *Use Dead-Letter Queues*<br>[reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) |

Notes
------------

1. Goose will use a standard queue in SQS instead of FIFO. Even with ordering guarantees from underlying queue, a multi-threaded worker like Goose cannot guarantee ordering of job-execution. Using a standard queue over FIFO will help save costs & more inflight messages.

Polymorphism
-------------

Use `defmulti` & `defmethod` as explained in [Aphyr's Polymorphism blog](https://aphyr.com/posts/352-clojure-from-the-ground-up-polymorphism#multimethods) to
elegate functionality based on brokers.
