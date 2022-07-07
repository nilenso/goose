ADR: Statsd Metrics
=============

Rationale
---------

- Client for statsd: [clj-statsd](https://github.com/pyr/clj-statsd). Factors taken into consideration:
  - No bloat
  - Simple 
  - Popular
  - Well-maintained
  

Avoided Designs
---------

- Avoided using Datadog's endorsed library [com.unbounce/clojure-dogstatsd-client](https://github.com/unbounce/clojure-dogstatsd-client)
  - unbounce sends arbitrary datadog stats prefixed `datadog.dogstatsd.client.*`. This can pollute user's metrics if they aren't using datadog
- Not supporting Prometheus-style pull metrics
  - Users of Prometheus can use [statsd-exporter](https://github.com/prometheus/statsd_exporter)
