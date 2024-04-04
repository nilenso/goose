(ns goose.brokers.redis.console
  (:require [bidi.bidi :as bidi]
            [clojure.string :as string]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.api.scheduled-jobs :as scheduled-jobs]
            [goose.brokers.redis.cron :as periodic-jobs]
            [goose.defaults :as d]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.util :as hiccup-util]
            [ring.util.response :as response])
  (:import
    (java.util Date)
    (java.lang Math)))

(defn- layout [& components]
  (fn [title {:keys [prefix-route] :as data}]
    (html5 [:head
            [:meta {:charset "UTF-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
            [:title title]
            (include-css (prefix-route "/css/style.css"))]
           [:body
            (map (fn [c] (c data)) components)])))

(defn- header [{:keys [app-name prefix-route] :or {app-name ""}}]
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

(defn- stats-bar [{:keys [prefix-route] :as page-data}]
  [:main
   [:section.statistics
    (for [{:keys [id label route]} [{:id :enqueued :label "Enqueued" :route "/enqueued"}
                                    {:id :scheduled :label "Scheduled" :route "/scheduled"}
                                    {:id :periodic :label "Periodic" :route "/periodic"}
                                    {:id :dead :label "Dead" :route "/dead"}]]
      [:div.stat {:id id}
       [:span.number (str (get page-data id))]
       [:a {:href (prefix-route route)}
        [:span.label label]]])]])

(defn- sidebar [{:keys [prefix-route queues queue]}]
  [:div#sidebar
   [:h3 "Queues"]
   [:div.queue-list
    [:ul
     (for [q queues]
       [:a {:href  (prefix-route "/enqueued/queue/" q)
            :class (when (= q queue) "highlight")}
        [:li.queue-list-item q]])]]])

(defn- enqueued-jobs-table [jobs]
  [:table.job-table
   [:thead
    [:tr
     [:th.id-h "Id"]
     [:th.execute-fn-sym-h "Execute fn symbol"]
     [:th.args-h "Args"]
     [:th.enqueued-at-h "Enqueued-at"]]]
   [:tbody
    (for [{:keys [id execute-fn-sym args enqueued-at]} jobs]
      [:tr
       [:td.id id]
       [:td.execute-fn-sym execute-fn-sym]
       [:td.args (string/join ", " args)]
       [:td.enqueued-at (str (Date. enqueued-at))]])]])

(defn pagination-stats [first-page curr-page last-page]
  {:first-page first-page
   :prev-page  (dec curr-page)
   :curr-page  curr-page
   :next-page  (inc curr-page)
   :last-page  last-page})

(defn- pagination [{:keys [prefix-route queue page total-jobs]}]
  (let [{:keys [first-page prev-page curr-page
                next-page last-page]} (pagination-stats (Integer/parseInt d/page) page
                                                        (Math/ceilDiv total-jobs d/page-size))
        page-uri (fn [p] (prefix-route "/enqueued/queue/" queue "?page=" p))
        hyperlink (fn [page label visible? disabled? & class]
                    (when visible?
                      [:a {:class (conj class (when disabled? "disabled"))
                           :href  (page-uri page)} label]))]
    [:div
     (hyperlink first-page (hiccup-util/escape-html "<<") ((comp not =) first-page curr-page last-page) (= curr-page first-page))
     (hyperlink prev-page prev-page (> curr-page first-page) false)
     (hyperlink curr-page curr-page ((comp not =) first-page curr-page last-page) true "highlight")
     (hyperlink next-page next-page (< curr-page last-page) false)
     (hyperlink last-page (hiccup-util/escape-html ">>") ((comp not =) first-page curr-page last-page) (= curr-page last-page))]))

(defn- enqueued-page-view [{:keys [jobs total-jobs] :as data}]
  [:div.redis-enqueued-main-content
   [:h1 "Enqueued Jobs"]
   [:div.content
    (sidebar data)
    [:div.right-side
     [:div.pagination
      (pagination data)]
     (enqueued-jobs-table jobs)
     (when (> total-jobs 0)
       [:div.bottom
        [:button.btn.btn-danger.btn-lg "Purge"]])]]])

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

(defn enqueued-page-data
  [redis-conn queue page]
  (let [page (Integer/parseInt (or page d/page))
        start (* (- page 1) d/page-size)
        end (- (* page d/page-size) 1)

        queues (enqueued-jobs/list-all-queues redis-conn)
        queue (or queue (first queues))
        total-jobs (enqueued-jobs/size redis-conn queue)
        jobs (enqueued-jobs/get-by-range redis-conn queue start end)]
    {:queues     queues
     :page       page
     :queue      queue
     :jobs       jobs
     :total-jobs total-jobs}))


(defn home-page [{:keys                     [prefix-route]
                  {:keys [app-name broker]} :console-opts}]
  (let [view (layout header stats-bar)
        data (jobs-size (:redis-conn broker))]
    (response/response (view "Home" (assoc data :app-name app-name
                                                :prefix-route prefix-route)))))


(defn enqueued-page [{:keys                     [prefix-route]
                      {:keys [app-name broker]} :console-opts
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

(defn- redirect-to-home-page [{:keys [prefix-route]}]
  (response/redirect (prefix-route "/")))

(defn- not-found [_]
  (response/not-found "<div> Not found </div>"))

(defn routes [route-prefix]
  [route-prefix [["" redirect-to-home-page]
                 ["/" home-page]
                 ["/enqueued" {""                 enqueued-page
                               ["/queue/" :queue] enqueued-page}]
                 ["/css/style.css" load-css]
                 ["/img/goose-logo.png" load-img]
                 [true not-found]]])

(defn handler [_ {:keys                  [uri]
                  {:keys [route-prefix]} :console-opts
                  :as                    req}]
  (let [{page-handler :handler
         route-params :route-params} (-> route-prefix
                                         routes
                                         (bidi/match-route uri))]
    (-> req
        (assoc :route-params route-params)
        page-handler)))
