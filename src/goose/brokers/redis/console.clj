(ns goose.brokers.redis.console
  (:require [hiccup.page :refer [html5 include-css]]
            [ring.util.response :as response]))

(defn landing-page [stats-map]
  (html5 [:head
          [:meta {:charset "UTF-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
          [:title "Goose Dashboard"]]
         (include-css "css/style.css")
         [:body
          [:header
           [:nav
            [:div.nav-start
             [:div#logo "AppName"]
             [:div#menu
              [:a {:href "/enqueued"} "Enqueued"]
              [:a {:href "/scheduled"} "Scheduled"]
              [:a {:href "/periodic"} "Periodic"]
              [:a {:href "/batch"} "Batch"]
              [:a {:href "/dead"} "Dead"]]]]]
          [:main
           [:section.statistics
            (for [stat [{:id :enqueued :label "Enqueued"}
                        {:id :scheduled :label "Scheduled"}
                        {:id :periodic :label "Periodic"}
                        {:id :dead :label "Dead"}]]
              [:div.stat {:id (:id stat)}
               [:span.number (str (get stats-map (:id stat)))]
               [:span.label (:label stat)]])]]]))

(defn handler [_broker {:keys [uri]}]
  (case uri
    "/" (response/response (landing-page {:enqueued  2
                                          :scheduled 10
                                          :periodic  12
                                          :dead      1}))
    (response/not-found "<div> Not found </div>")))
