(ns goose.metrics.statsd
  "StatsD is the default & specimen plugin for Goose.
  Plugins can be customized by implementing Metrics protocol."
  (:require
    [goose.metrics :as m]
    [goose.specs :as specs]

    [clj-statsd]))

(defn- build-tags
  [tags]
  (map (fn [[key value]] (str (name key) ":" value)) tags))

(defmacro ^:private with-merged-tags
  [f metric value sample-rate tags additional-tags]
  `(let [tags# (build-tags (merge ~tags ~additional-tags))]
     (~f ~metric ~value ~sample-rate tags#)))

(defrecord StatsD [enabled? sample-rate tags]
  m/Metrics
  (enabled? [this] (:enabled? this))
  (gauge [this key value additional-tags]
    (with-merged-tags clj-statsd/gauge key value (:sample-rate this) (:tags this) additional-tags))
  (increment [this key value additional-tags]
    (with-merged-tags clj-statsd/increment key value (:sample-rate this) (:tags this) additional-tags))
  (timing [this key duration additional-tags]
    (with-merged-tags clj-statsd/timing key duration (:sample-rate this) (:tags this) additional-tags)))

(def default-opts
  "Map of sample config for StatsD Metric Backend.

  ### Keys
  `:enabled?`    : Boolean flag for enabling/disabling metrics.

  `:host`        : Host of StatsD Aggregator.

  `:port`        : Port of StatsD Aggregator.

  `:prefix`      : Prefix for all metrics.\\
  Can be a generic term like `\"goose.\"` or specific to microservice name.

  `:sample-rate` : Sample rate of metric collection.

  `:tags`        : Map of key-value pairs to be attached to every metric."
  {:enabled?    true
   :host        "localhost"
   :port        8125
   :prefix      "goose."
   :sample-rate 1.0
   :tags        {}})

(defn new
  "Creates a Metrics implementation for StatsD Backend.

  ### Args

  `opts`  : Map of `:enabled?`, `:host`, `:port`, `:prefix`, `:sample-rate` & `:tags`.\\
  Example : [[default-opts]]"
  [{:keys [enabled? host port prefix] :as opts}]
  (specs/assert-statsd opts)
  (when enabled?
    (clj-statsd/setup host port :prefix prefix))
  (map->StatsD opts))
