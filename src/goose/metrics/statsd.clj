(ns goose.metrics.statsd
  "StatsD is the default & specimen plugin for Goose.
  Plugins can be customized by implementing Metrics protocol."
  (:require
    [goose.metrics.protocol :as protocol]

    [clj-statsd]))

(defn- build-tags
  [tags]
  (map (fn [[key value]] (str (name key) ":" value)) tags))

(defn- with-merged-tags
  [f metric value sample-rate user-tags goose-tags]
  (let [tags (build-tags (merge user-tags goose-tags))]
    (f metric value sample-rate tags)))

(defrecord StatsD [enabled? sample-rate user-tags]
  protocol/Protocol
  (enabled? [this] (:enabled? this))
  (gauge [this key value goose-tags]
    (with-merged-tags clj-statsd/gauge key value (:sample-rate this) (:user-tags this) goose-tags))
  (increment [this key value goose-tags]
    (with-merged-tags clj-statsd/increment key value (:sample-rate this) (:user-tags this) goose-tags))
  (timing [this key duration goose-tags]
    (with-merged-tags clj-statsd/timing key duration (:sample-rate this) (:user-tags this) goose-tags)))

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
  [{:keys [enabled? host port prefix sample-rate tags]}]
  (when enabled?
    (clj-statsd/setup host port :prefix prefix))
  (->StatsD enabled? sample-rate tags))
