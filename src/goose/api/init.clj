(ns goose.api.init
  (:require
    [goose.brokers.broker :as broker]
    [goose.redis :as r]))

(def broker-conn (atom nil))

(defn initialize
  [broker-opts]
  (let [broker-opts (broker/create broker-opts)]
    (reset! broker-conn (r/conn broker-opts))))
