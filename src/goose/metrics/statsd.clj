(ns goose.metrics.statsd
  "StatsD is the default plugin for Goose.
  Users can choose custom plugins by
  implementing Metrics protocol."
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

(defrecord StatsD [enabled sample-rate user-tags]
  protocol/Protocol
  (enabled? [_] enabled)
  (gauge [_ key value goose-tags]
    (when enabled
      (with-merged-tags clj-statsd/gauge key value sample-rate user-tags goose-tags)))
  (increment [_ key value goose-tags]
    (when enabled
      (with-merged-tags clj-statsd/increment key value sample-rate user-tags goose-tags)))
  (timing [_ key duration goose-tags]
    (when enabled
      (with-merged-tags clj-statsd/timing key duration sample-rate user-tags goose-tags))))

(def default-opts
  "Default config for StatsD Metrics."
  {:enabled     true
   :host        "localhost"
   :port        8125
   :prefix      "goose."
   :sample-rate 1.0
   :tags        {}})

(defn new
  "Create a StatsD Metrics plugin.
  Prefix metrics to distinguish between 2 microservices."
  [{:keys [enabled host port prefix sample-rate tags]}]
  (when enabled
    (clj-statsd/setup host port :prefix prefix))
  (->StatsD enabled sample-rate tags))


