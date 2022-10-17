(ns goose.metrics.statsd
  "StatsD is the default & specimen plugin for Goose.
  Plugins can be customized by implementing Metrics protocol."
  (:require
    [goose.metrics :as m]

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
  "Default config for StatsD Metrics."
  {:enabled?    true
   :host        "localhost"
   :port        8125
   :prefix      "goose."
   :sample-rate 1.0
   :tags        {}})

(defn new
  "Create a StatsD Metrics plugin.
  Prefix metrics to distinguish between 2 microservices."
  [{:keys [enabled? host port prefix] :as opts}]
  (when enabled?
    (clj-statsd/setup host port :prefix prefix))
  (map->StatsD opts))
