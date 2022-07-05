ADR: Statsd Metrics
=============

Rationale
---------

- Chose [com.unbounce/clojure-dogstatsd-client](https://github.com/unbounce/clojure-dogstatsd-client) over [tech.gojek/meajurements](https://github.com/gojekfarm/meajurements)
  - unbounce is officially endorsed by Datadog, is more popular, and users can reuse their existing config
  - unbounce has support for sample-rate config

Avoided Designs
---------

- 
