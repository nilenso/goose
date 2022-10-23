ADR: Pluggable Message Brokers
========
Goose supports RabbitMQ & Redis out of the box.

Users can inject implementation of a message broker that satisfies `goose.broker/Broker` protocol.

[Guide to Message Broker Integration can be found in the wiki](https://github.com/nilenso/goose/wiki/Guide-to-Message-Broker-Integration). Looking forward to Pull Requests for Postgres, Amazon SQS implementations🤞

Rationale
---------

- Since brokers need state of connection-pooling, `defprotocol` was preferable to other polymorphism constructs like `defmulti`
