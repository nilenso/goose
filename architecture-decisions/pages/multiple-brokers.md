ADR: Multiple Brokers
=============

Approach
-------------

| Feature | Redis | RabbitMQ | Amazon SQS |
| ----------- | ----------- | ----------- | ----------- |
| Enqueue-Dequeue | `LPUSH`, `BRPOPLPUSH`, `LREM` | `QUEUE(durable: true)`<br>`PUBLISH(persistent: true)`<br>`SUBSCRIBE(manual_ack: true)`<br>`channel.ACK`<br>[reference](https://www.rabbitmq.com/tutorials/tutorial-two-ruby.html) | `PUSH`<br>`RECEIVE(with visibility timeouts)`<br>`DELETE`<br>[reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) |
| feature | redis | rmq | sqs |

Features which are straight-forward:
- Priority/Customised queues
- 

Interface
-------------
