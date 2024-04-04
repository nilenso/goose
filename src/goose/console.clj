(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]))

(defn- wrap-prefix-route [handler]
  (fn [{{:keys [route-prefix]} :console-opts
        :as req}]
    (let [prefix-route-fn (partial str route-prefix)]
      (handler (assoc req :prefix-route prefix-route-fn)))))

(defn app-handler [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       wrap-prefix-route
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
