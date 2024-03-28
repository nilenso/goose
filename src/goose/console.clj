(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]))

(defn- handler [{{:keys [broker]} :client-opts :as req}]
  (b/handler broker req))

(defn app-handler [client-opts req]
  ((-> handler
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :client-opts client-opts)))
