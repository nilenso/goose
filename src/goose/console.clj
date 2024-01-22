(ns goose.console
  (:require [goose.broker :as b]
            [ring.middleware.resource :refer :all]))

(defn handler [{:keys [broker] :as req}]
  (b/handler broker req))
