(ns goose.console
  (:require [goose.broker :as b]))

(defn- handler [{{:keys [broker]} :console-opts :as req}]
  (b/handler broker req))

(defn app-handler [console-opts req]
  (handler (assoc req :console-opts console-opts)))
