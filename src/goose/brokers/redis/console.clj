(ns goose.brokers.redis.console
  (:require [hiccup.page :refer [html5 include-css]]
            [ring.util.response :as response]

            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]))

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

(defn handler [broker {:keys [uri]}]
  (case uri
    "/" (response/response (landing-page (jobs-size broker)))
    (response/not-found "<div> Not found </div>")))
