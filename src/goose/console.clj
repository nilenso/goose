(ns goose.console
  (:require [goose.broker :as b]))

(defn app-handler [{:keys [broker] :as console-opts} req]
  (b/handler broker (assoc req :console-opts console-opts)))
