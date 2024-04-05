(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]))

(defn- wrap-prefix-route
  "Middleware that injects a `prefix-route` function into the request,
  that facilitates URL construction in views by prepending given route-prefix to paths"
  [handler]
  (fn [{{:keys [route-prefix]} :console-opts
        :as req}]
    (let [prefix-route-fn (partial str route-prefix)]
      (handler (assoc req :prefix-route prefix-route-fn)))))

(defn wrap-method-override
  "Middleware to override HTTP method based on the `_method` parameters in request params, allowing
  to simulate PUT, DELETE, PATCH etc. methods that are not supported in HTML forms"
  [handler]
  (fn [request]
    (if-let [overridden-method (get-in request [:params :_method])]
      (handler (assoc request :request-method (keyword overridden-method)))
      (handler request))))

(defn app-handler [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       wrap-prefix-route
       wrap-method-override
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
