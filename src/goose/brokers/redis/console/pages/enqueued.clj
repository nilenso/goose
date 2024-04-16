(ns goose.brokers.redis.console.pages.enqueued
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.defaults :as d]
            [hiccup.util :as hiccup-util]
            [ring.util.response :as response])
  (:import
    (java.lang Math)
    (java.util Date)))

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
       [:td.enqueued-at (Date. ^Long enqueued-at)]])]])

(defn pagination-stats [first-page curr-page last-page]
  {:first-page first-page
   :prev-page  (dec curr-page)
   :curr-page  curr-page
   :next-page  (inc curr-page)
   :last-page  last-page})

(defn- pagination [{:keys [prefix-route queue page total-jobs]}]
  (let [{:keys [first-page prev-page curr-page
                next-page last-page]} (pagination-stats d/page page
                                                        (Math/ceilDiv ^Integer total-jobs ^Integer d/page-size))
        page-uri (fn [p] (prefix-route "/enqueued/queue/" queue "?page=" p))
        hyperlink (fn [page label visible? disabled? & class]
                    (when visible?
                      [:a {:class (conj class (when disabled? "disabled"))
                           :href  (page-uri page)} label]))
        single-page? (<= total-jobs d/page-size)]
    [:div
     (hyperlink first-page (hiccup-util/escape-html "<<") (not single-page?) (= curr-page first-page))
     (hyperlink prev-page prev-page (> curr-page first-page) false)
     (hyperlink curr-page curr-page (not single-page?) true "highlight")
     (hyperlink next-page next-page (< curr-page last-page) false)
     (hyperlink last-page (hiccup-util/escape-html ">>") (not single-page?) (= curr-page last-page))]))

(defn confirmation-dialog [{:keys [prefix-route queue]}]
  [:dialog {:class "purge-dialog"}
   [:div "Are you sure, you want to purge the " [:span.highlight queue] " queue?"]
   [:form {:action (prefix-route "/enqueued/queue/" queue)
           :method "post"
           :class  "dialog-btns"}
    [:input {:name "_method" :type "hidden" :value "delete"}]
    [:input {:name "queue" :value queue :type "hidden"}]
    [:input {:type "button" :value "Cancel" :class "btn btn-md btn-cancel cancel"}]
    [:input {:type "submit" :value "Confirm" :class "btn btn-danger btn-md"}]]])

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
        (confirmation-dialog data)
        [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]])]]])

(defn page [{:keys                     [prefix-route]
             {:keys [app-name broker]} :console-opts
             {:keys [page]}            :params
             {:keys [queue]}           :route-params}]
  (let [view (c/layout c/header enqueued-page-view)
        data (data/enqueued-page-data (:redis-conn broker) queue page)]
    (response/response (view "Enqueued" (assoc data :app-name app-name
                                                    :prefix-route prefix-route)))))

(defn purge-queue [{{:keys [broker]} :console-opts
                    {:keys [queue]}  :params
                    :keys            [prefix-route]}]
  (enqueued-jobs/purge (:redis-conn broker) queue)
  (response/redirect (prefix-route "/enqueued")))
