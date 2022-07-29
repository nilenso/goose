ADR: Statsd Metrics
=============

Rationale
---------

- Client for statsd: [clj-statsd](https://github.com/pyr/clj-statsd). Factors taken into consideration:
  - No bloat
  - Simple 
  - Popular
  - Well-maintained
- Dynamic data like queue & function name is set as tags to help filter in dashboards/alerts
- For gauge data-type, dynamic data is embedded in the metric. For ex: `enqueued.my-queue.size`

Avoided Designs
---------

- Avoided using Datadog's endorsed library [com.unbounce/clojure-dogstatsd-client](https://github.com/unbounce/clojure-dogstatsd-client)
  - unbounce sends arbitrary datadog stats prefixed `datadog.dogstatsd.client.*`. This can pollute user's metrics if they aren't using datadog
- Not supporting Prometheus-style pull metrics
  - Users of Prometheus can use [statsd-exporter](https://github.com/prometheus/statsd_exporter)
