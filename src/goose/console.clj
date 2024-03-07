(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]))


(defn app-handler [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
