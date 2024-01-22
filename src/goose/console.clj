(ns goose.console
  (:require [goose.broker :as b]
            [clojure.string :as string]
            [ring.middleware.resource :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn wrap-remove-route-prefix [handler]
  (fn [{:keys [route-prefix] :as req}]
    (handler (update req :uri #(-> %1
                                   re-pattern
                                   (string/replace route-prefix ""))))))

(defn handler [{:keys [broker] :as req}]
  (b/handler broker req))

(def app-handler
  (-> handler
      (wrap-resource "public")
      wrap-remove-route-prefix))
