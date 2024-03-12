(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]))

(defn- wrap-prefix-route [handler]
  (fn [{{:keys [route-prefix]} :client-opts
        :as req}]
    (let [prefix-route-fn (partial str route-prefix)]
      (handler (assoc req :prefix-route prefix-route-fn)))))

(defn- handler [{{:keys [broker]} :client-opts :as req}]
  (b/handler broker req))

(defn app-handler [client-opts req]
  ((-> handler
       wrap-prefix-route
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :client-opts client-opts)))
