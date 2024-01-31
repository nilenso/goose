(ns goose.brokers.redis.console
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [hiccup.page :refer [html5 include-css]]
            [ring.util.response :as response]))

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

(defn- header [{:keys [app-name] :or {app-name ""}}]
  [:header
   [:nav
    [:div.nav-start
     [:div.goose-logo
      [:a {:href ""}
       [:img {:src "img/goose-logo.png" :alt "goose-logo"}]]]
     [:a {:href ""}
      [:div#app-name app-name]]
     [:div#menu
      [:a {:href "enqueued"} "Enqueued"]
      [:a {:href "scheduled"} "Scheduled"]
      [:a {:href "periodic"} "Periodic"]
      [:a {:href "batch"} "Batch"]
      [:a {:href "dead"} "Dead"]]]]])

(defn- stats-bar [page-data]
  [:main
   [:section.statistics
    (for [stat [{:id :enqueued :label "Enqueued" :route "enqueued"}
                {:id :scheduled :label "Scheduled" :route "scheduled"}
                {:id :periodic :label "Periodic" :route "periodic"}
                {:id :dead :label "Dead" :route "dead"}]]
      [:div.stat {:id (:id stat)}
       [:span.number (str (get page-data (:id stat)))]
       [:a {:href (:route stat)}
        [:span.label (:label stat)]]])]])

;Broker Data
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

;;Page handler
(defn- home-page [broker {{:keys [app-name]} :client-opts}]
  (let [view (layout header stats-bar)
        data (jobs-size broker)]
    (response/response (view "Home" (assoc data :app-name app-name)))))

(defn handler [broker {:keys [uri]
                       {:keys [route-prefix]} :client-opts
                       :as req}]
  (let [path (-> uri
                 re-pattern
                 (string/replace route-prefix ""))]
    (case path
      "" (response/redirect (str route-prefix "/"))
      "/" (home-page broker req)
      "/css/style.css" (response/resource-response "css/style.css")
      "/img/goose-logo.png" (response/resource-response "img/goose-logo.png")
      (response/not-found "<div> Not found </div>"))))
