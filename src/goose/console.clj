(ns goose.console
  (:require [clojure.string :as string]
            [ring.middleware.resource :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]

            [goose.broker :as b]
            [goose.utils :as utils]))

(defn- wrap-remove-route-prefix [handler]
  (fn [{:keys [route-prefix] :as req}]
    (handler (update req :uri #(-> %1
                                   re-pattern
                                   (string/replace route-prefix ""))))))

(defn- wrap-prefixed-route [handler]
  (fn [{:keys [route-prefix] :as req}]
    (let [prefixed-route-fn (utils/route-prefixer route-prefix)]
      (handler (assoc req :prefixed-route prefixed-route-fn)))))

(defn- handler [{:keys [broker] :as req}]
  (b/handler broker req))

(def app-handler
  (-> handler
      (wrap-resource "public")
      wrap-prefixed-route
      wrap-remove-route-prefix))
