(ns goose.brokers.redis.console.pages.home
  (:require [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.console :as console]
            [ring.util.response :as response]))

(defn- stats-bar [{:keys [prefix-route] :as page-data}]
  [:main
   [:section.statistics
    (for [{:keys [id label route]} [{:id :enqueued :label "Enqueued" :route "/enqueued"}
                                    {:id :scheduled :label "Scheduled" :route "/"} ;; Routed to homepage since pages are non-existent
                                    {:id :periodic :label "Periodic" :route "/"}
                                    {:id :dead :label "Dead" :route "/"}]]
      [:div.stat {:id id}
       [:span.number (str (get page-data id))]
       [:a {:href (prefix-route route)}
        [:span.label label]]])]])

(defn page [{:keys                     [prefix-route uri]
             {:keys [app-name broker]} :console-opts}]
  (let [view (console/layout c/header stats-bar)
        data (data/jobs-size (:redis-conn broker))]
    (response/response (view "Home" (assoc data :uri uri
                                                :app-name app-name
                                                :prefix-route prefix-route)))))
