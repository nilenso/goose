ADR: Metrics
=============

Goose emits `StatsD` metrics by default. Users can also inject metric backends that satisfy the Metrics protocol.

Rationale
---------

- Client for statsd: [clj-statsd](https://github.com/pyr/clj-statsd). Factors taken into consideration:
  - No bloat
  - Simple
  - Popular
  - Well-maintained
- Dynamic data like queue & function name is set as tags to help filter in dashboards/alerts
- For gauge data-type, dynamic data is embedded in the metric. For ex: `enqueued.my-queue.size`
- Setting a prefix helps differentiate between 2 microservices

### RabbitMQ

- RabbitMQ doesn't have an internal metrics-runner that monitors queue size. Reasons:
  - RMQ API has different port than AMQP. Initializing broker for an API adds unnecessary complications
  - There's no way to find number of active worker processes & arrive at a sleep time of 1 min
  - With vhosts, finding total list of queues becomes quite complicated
- To get RMQ queue-size metrics, use plugins of metric providers:
  - [Datadog](https://docs.datadoghq.com/integrations/rabbitmq)
  - [Telegraf](https://github.com/influxdata/telegraf/tree/master/plugins/inputs/rabbitmq)
  - [Prometheus](https://www.rabbitmq.com/prometheus.html)

Avoided Designs
---------

- Avoided using Datadog's endorsed library [com.unbounce/clojure-dogstatsd-client](https://github.com/unbounce/clojure-dogstatsd-client)
  - unbounce sends arbitrary datadog stats prefixed `datadog.dogstatsd.client.*`. This can pollute user's metrics if they aren't using datadog
- Not supporting Prometheus-style pull metrics
  - Users of Prometheus can use [statsd-exporter](https://github.com/prometheus/statsd_exporter)
