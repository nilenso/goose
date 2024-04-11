(ns goose.brokers.redis.console.pages.components
  (:require [hiccup.page :refer [html5 include-css include-js]]))

(defn layout [& components]
  (fn [title {:keys [prefix-route] :as data}]
    (html5 {:lang "en"}
           [:head
            [:meta {:charset "UTF-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
            [:title title]
            (include-css (prefix-route "/css/style.css"))
            (include-js (prefix-route "/js/index.js"))]
           [:body
            (map (fn [c] (c data)) components)])))

(defn header [{:keys [app-name prefix-route] :or {app-name ""}}]
  (let [short-app-name (if (> (count app-name) 20)
                         (str (subs app-name 0 17) "..")
                         app-name)]
    [:header
     [:nav
      [:div.nav-start
       [:div.goose-logo
        [:a {:href ""}
         [:img {:src (prefix-route "/img/goose-logo.png") :alt "goose-logo"}]]]
       [:div#menu
        [:a {:href (prefix-route "") :class "app-name"} short-app-name]
        [:a {:href (prefix-route "/enqueued")} "Enqueued"]]]]]))
