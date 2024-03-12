(ns goose.brokers.redis.console
  (:require [bidi.bidi :as bidi]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [hiccup.page :refer [html5 include-css]]
            [ring.util.response :as response]))

(defn- layout [& components]
  (fn [title {:keys [prefix-route] :as data}]
    (html5 [:head
            [:meta {:charset "UTF-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
            [:title title]
            (include-css (prefix-route "/css/style.css"))]
           [:body
            (map (fn [c] (c data)) components)])))

(defn- header [{:keys [app-name
                       prefix-route] :or {app-name ""}}]
  [:header
   [:nav
    [:div.nav-start
     [:div.goose-logo
      [:a {:href (prefix-route "")}
       [:img {:src (prefix-route "/img/goose-logo.png") :alt "goose-logo"}]]]
     [:a {:href (prefix-route "")}
      [:div#app-name app-name]]
     [:div#menu
      [:a {:href (prefix-route "/enqueued")} "Enqueued"]
      [:a {:href (prefix-route "/scheduled")} "Scheduled"]
      [:a {:href (prefix-route "/periodic")} "Periodic"]
      [:a {:href (prefix-route "/batch")} "Batch"]
      [:a {:href (prefix-route "/dead")} "Dead"]]]]])

(defn- stats-bar [{:keys [prefix-route] :as page-data}]
  [:main
   [:section.statistics
    (for [stat [{:id :enqueued :label "Enqueued" :route "/enqueued"}
                {:id :scheduled :label "Scheduled" :route "/scheduled"}
                {:id :periodic :label "Periodic" :route "/periodic"}
                {:id :dead :label "Dead" :route "/dead"}]]
      [:div.stat {:id (:id stat)}
       [:span.number (str (get page-data (:id stat)))]
       [:a {:href (prefix-route (:route stat))}
        [:span.label (:label stat)]]])]])

(defn sidebar [{:keys [prefix-route queues]}]
  [:div#sidebar
   [:h3 "Queues"]
   [:div.queue-list
    [:ul
     (for [queue queues]
       [:a {:href (prefix-route "/enqueued/queue/" queue)} [:li.queue-list-item queue]])]]])

(defn sticky-header []
  [:div.header
   [:div.filter-opts
    [:div.filter-opts-items
     [:select {:name "filter" :id "filter"}
      [:option {:value "id"} "id"]
      [:option {:value "execute-fn-sym"} "execute-fn-sym"]
      [:option {:value "type"} "type"]]
     [:input {:type "text" :name "filter" :id "filter" :placeholder "filter value"}]]
    [:div.filter-opts-items
     [:span.limit "Limit"]
     [:input {:type "text" :name "limit" :id "limit" :placeholder "custom limit"}]]
    [:div.filter-opts-items
     [:button.btn.btn-action "Clear"]
     [:button.btn "Apply"]]]
   [:div.action-buttons
    [:button.btn "Prioritise"]
    [:button.btn.btn-danger "Delete"]]])

(defn enqueued-jobs-table [jobs]
  [:table.job-table
   [:thead
    [:tr
     [:th.id "Id"]
     [:th.execute-fn-sym "Execute-in-symbol"]
     [:th.args "Args"]
     [:th.enqueued-at "Enqueued-at"]
     [:th.select [:input {:type "checkbox" :name "" :id ""}]]]]
   [:tbody
    (for [job jobs]
      [:tr
       [:td (:id job)]
       [:td (:execute-fn-sym job)]
       [:td (:args job)]
       [:td (:enqueued-at job)]
       [:td [:input {:type "checkbox" :name "" :id ""}]]])]])

(defn enqueued-page-view [{:keys [prefix-route] :as data}]
  [:div.redis-enqueued-main-content
   [:h1 "Enqueued Jobs"]
   [:div.content
    (sidebar data)
    [:div.right-side
     (sticky-header)
     [:div.pagination
      [:a {:href (prefix-route "/enqueued/queue/" (:queue data) "?page=1")} "1"]
      [:a {:href (prefix-route "/enqueued/queue/" (:queue data) "?page=2")} "2"]
      [:div ".."]
      [:a {:href (prefix-route "/enqueued/queue/" (:queue data) "?page=" (inc (:page data)))}
       ">"]]
     (enqueued-jobs-table (:jobs data))
     [:div.bottom
      [:button.btn.btn-danger.btn-md "Purge"]]]]])

(defn jobs-size [redis-conn]
  (let [queues (enqueued-jobs/list-all-queues redis-conn)
        enqueued (reduce (fn [total queue]
                           (+ total (enqueued-jobs/size redis-conn queue))) 0 queues)
        scheduled (scheduled-jobs/size redis-conn)
        periodic (periodic-jobs/size redis-conn)
        dead (dead-jobs/size redis-conn)]
    {:enqueued  enqueued
     :scheduled scheduled
     :periodic  periodic
     :dead      dead}))

(def page-size 10)

(defn enqueued-page-data
  [redis-conn queue page]
  (let [page (Integer/parseInt (or page "1"))
        start (* (- page 1) page-size)
        end (- (* page page-size) 1)

        queues (enqueued-jobs/list-all-queues redis-conn)
        queue (or queue (first queues))

        jobs (enqueued-jobs/get-by-range redis-conn queue start end)]
    {:queues queues
     :page page
     :queue  queue
     :jobs   jobs}))

(defn home-page [{:keys            [prefix-route]
                  {:keys [app-name
                          broker]} :client-opts}]
  (let [view (layout header stats-bar)
        data (jobs-size (:redis-conn broker))]
    (response/response (view "Home" (assoc data :app-name app-name
                                                :prefix-route prefix-route)))))

(defn enqueued-page [{:keys                     [prefix-route]
                      {:keys [app-name broker]} :client-opts
                      {:keys [page]}            :params
                      {:keys [queue]}           :route-params}]
  (let [view (layout header enqueued-page-view)
        data (enqueued-page-data (:redis-conn broker) queue page)]
    (response/response (view "Enqueued" (assoc data :app-name app-name
                                                    :prefix-route prefix-route)))))

(defn- load-css [_]
  (-> "css/style.css"
      response/resource-response
      (response/header "Content-Type" "text/css")))

(defn- load-img [_]
  (-> "img/goose-logo.png"
      response/resource-response
      (response/header "Content-Type" "image/png")))

(defn- redirect-to-home-page [{{:keys [route-prefix]} :client-opts}]
  (response/redirect (str route-prefix "/")))

(defn- not-found [_]
  (response/not-found "<div> Not found </div>"))

(def route-handlers
  {:home-page             home-page
   :load-css              load-css
   :load-img              load-img
   :enqueued-page         enqueued-page
   :redirect-to-home-page redirect-to-home-page
   :not-found             not-found})

(defn routes [route-prefix]
  [route-prefix [["" :redirect-to-home-page]
                 ["/" :home-page]
                 ["/enqueued" {""                 :enqueued-page
                               ["/queue/" :queue] :enqueued-page}]
                 ["/css/style.css" :load-css]
                 ["/img/goose-logo.png" :load-img]
                 [true :not-found]]])

(defn handler [_ {:keys                  [uri]
                  {:keys [route-prefix]} :client-opts
                  :as                    req}]
  (let [{:keys [handler route-params]} (bidi/match-route (routes route-prefix) uri)
        handler-fn (get route-handlers handler)
        req (assoc req :route-params route-params)]
    (handler-fn req)))
