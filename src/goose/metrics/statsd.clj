(ns goose.metrics.statsd
  "StatsD is the default plugin for Goose.
  Users can choose custom plugins by
  implementing Metrics protocol."
  (:require
    [goose.metrics.keys :as keys]
    [goose.metrics.protocol :as protocol]

    [clj-statsd]))

(defn build-tags
  [tags]
  (map (fn [[key value]] (str (name key) ":" value)) tags))

(defn with-merged-tags
  [f metric value sample-rate user-tags goose-tags]
  (let [tags (build-tags (merge user-tags goose-tags))]
    (f metric value sample-rate tags)))

(defrecord StatsD [enabled? sample-rate user-tags]
  protocol/Protocol
  (enabled? [_] enabled?)
  (gauge [_ metric value goose-tags]
    (when enabled?
      (with-merged-tags clj-statsd/gauge metric value sample-rate user-tags goose-tags)))
  (increment [_ metric value goose-tags]
    (when enabled?
      (with-merged-tags clj-statsd/increment metric value sample-rate user-tags goose-tags)))
  (timing [_ metric duration goose-tags]
    (when enabled?
      (with-merged-tags clj-statsd/timing metric duration sample-rate user-tags goose-tags))))

(def default-opts
  "Default config for StatsD Metrics."
  {:enabled?    true
   :host        "localhost"
   :port        8125
   :sample-rate 1.0
   :tags        {}})

(defn new
  [{:keys [enabled? host port sample-rate tags]}]
  (when enabled?
    (clj-statsd/setup host port :prefix keys/prefix))
  (->StatsD enabled? sample-rate tags))


