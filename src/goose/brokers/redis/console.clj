(ns goose.brokers.redis.console
  (:require [hiccup.page :refer [html5 include-css]]
            [ring.util.response :as response]

            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]))

;View
(defn- layout [& components]
  (fn [title data]
    (html5 [:head
            [:meta {:charset "UTF-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
            [:title title]
            (include-css "css/style.css")]
           [:body
            (map (fn [c] (c data)) components)])))

(defn- header [{:keys [prefixed-route app-name] :or {app-name ""}}]
  [:header
   [:nav
    [:div.nav-start
     [:div.goose-logo
      [:a {:href (prefixed-route "/")}
       [:img {:src "img/goose-logo.png" :alt "goose-logo"}]]]
     [:a {:href (prefixed-route "/")}
      [:div#app-name app-name]]
     [:div#menu
      [:a {:href (prefixed-route "/enqueued")} "Enqueued"]
      [:a {:href (prefixed-route "/scheduled")} "Scheduled"]
      [:a {:href (prefixed-route "/periodic")} "Periodic"]
      [:a {:href (prefixed-route "/batch")} "Batch"]
      [:a {:href (prefixed-route "/dead")} "Dead"]]]]])

(defn- stats-bar [{:keys [prefixed-route] :as page-data}]
  [:main
   [:section.statistics
    (for [stat [{:id :enqueued :label "Enqueued" :route (prefixed-route "/enqueued")}
                {:id :scheduled :label "Scheduled" :route (prefixed-route "/scheduled")}
                {:id :periodic :label "Periodic" :route (prefixed-route "/periodic")}
                {:id :dead :label "Dead" :route (prefixed-route "/dead")}]]
      [:div.stat {:id (:id stat)}
       [:span.number (str (get page-data (:id stat)))]
       [:a {:href (:route stat)}
        [:span.label (:label stat)]]])]])

;Data
(defn- jobs-size [broker]
  (let [queues (enqueued-jobs/list-all-queues broker)
        enqueued (reduce (fn [total queue]
                           (+ total (enqueued-jobs/size broker queue))) 0 queues)
        scheduled (scheduled-jobs/size broker)
        periodic (periodic-jobs/size broker)
        dead (dead-jobs/size broker)]
    {:enqueued  enqueued
     :scheduled scheduled
     :periodic  periodic
     :dead      dead}))

(defn- home-page [broker {:keys [app-name prefixed-route]}]
  (let [view (layout header stats-bar)
        data (jobs-size broker)]
    (response/response (view "Home" (assoc data :app-name app-name
                                                :prefixed-route prefixed-route)))))

(defn handler [broker {:keys [uri] :as req}]
  (case uri
    "/" (home-page broker req)
    (response/not-found "<div> Not found </div>")))
