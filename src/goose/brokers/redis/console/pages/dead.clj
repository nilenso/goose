(ns goose.brokers.redis.console.pages.dead
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.utils :as utils]
            [ring.util.response :as response])
  (:import
    (java.util Date)))

(defn jobs-table [{:keys [prefix-route jobs]}]
  [:form {:action (prefix-route "/dead/jobs")
          :method "post"}
   [:table.jobs-table
    [:thead
     [:tr
      [:th.id-h "Id"]
      [:th.queue-h "Queue"]
      [:th.execute-fn-sym-h "Execute fn symbol"]
      [:th.args-h "Args"]
      [:th.enqueued-at-h "Died at"]
      [:th.checkbox-h [:input {:type "checkbox" :id "checkbox-h"}]]]]
    [:tbody
     (for [{:keys             [id queue execute-fn-sym args] :as j
            {:keys [died-at]} :state} jobs]
       [:tr
        [:td [:a {:href  (prefix-route "/dead/job/" id)
                  :class "underline"}
              [:div.id id]]]
        [:td [:div.queue] queue]
        [:td [:div.execute-fn-sym (str execute-fn-sym)]]
        [:td [:div.args (string/join ", " (mapv c/format-arg args))]]
        [:td [:div.died-at] (Date. ^Long died-at)]
        [:td [:div.checkbox-div
              [:input {:name  "jobs"
                       :type  "checkbox"
                       :class "checkbox"
                       :value (utils/encode-to-str j)}]]]])]]])

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis
   [:h1 "Dead Jobs"]
   [:div.content.redis-jobs-page
    [:div.pagination
     (jobs-table data)]
    (when (and total-jobs (> total-jobs 0)))
    [:div.bottom
     (c/purge-confirmation-dialog data)
     [:button.btn.btn-danger.btn-lg.purge-dialog-show "Purge"]]]])

(defn get-jobs [{:keys                     [prefix-route]
                 {:keys [app-name broker]} :console-opts
                 {:keys [page]}            :params}]
  (let [view (console/layout c/header jobs-page-view)
        current-page (or d/page page)
        jobs (dead-jobs/get-by-range (:redis-conn broker) (* d/page-size (dec current-page)) (* d/page-size current-page))
        data {:page       current-page
              :jobs       jobs
              :total-jobs (dead-jobs/size (:redis-conn broker))}]
    (response/response (view "Dead" (assoc data :job-type :dead
                                                :app-name app-name
                                                :prefix-route prefix-route)))))
