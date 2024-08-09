(ns goose.brokers.redis.console.pages.periodic
  (:require [clojure.string :as str]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.brokers.redis.cron :as periodic]
            [goose.console :as console] 
            [ring.util.response :as response])
  (:import [it.burning.cron CronExpressionDescriptor]))

(defn jobs-table [{:keys [base-path jobs]}]
  [:form {:action (str base-path "/jobs")
          :method "post"}
   [:div.padding-top
    (c/action-btns [(c/delete-btn
                      [:div "Are you sure you want to delete selected jobs?"])])]
   [:table.jobs-table
    [:thead
     [:th.name "Name"]
     [:th.schedule-h "Schedule"]
     [:th.timezone "Timezone"]
     [:th.queue-h "Queue"]
     [:th.execute-fn-sym-h "Execute fn symbol"]
     [:th.args-h "Args"]
     [:th.checkbox-h [:input {:type "checkbox" :id "checkbox-h"}]]]
    [:tbody
     (for [{:keys                               [cron-name timezone cron-schedule]
            {:keys [args queue execute-fn-sym]} :job-description
            :as                                 j} jobs]
       [:tr
        [:td [:div.name cron-name]]
        [:td [:div.schedule.blue.tooltip
              cron-schedule
              [:span.tooltip-text
               [:div.tooltip-content
                (CronExpressionDescriptor/getDescription cron-schedule)]]]]
        [:td [:div.timezone timezone]]
        [:td [:div.queue] queue]
        [:td [:div.execute-fn-sym (str execute-fn-sym)]]
        [:td [:div.args (str/join ", " (mapv console/format-arg args))]]
        [:td [:div.checkbox-div
              [:input {:name  "cron-names"
                       :type  "checkbox"
                       :class "checkbox"
                       :value cron-name}]]]])]]])

(defn- filter-header [{:keys                  [base-path job-type]
                       {:keys [filter-value]} :params}]
  [:div.header
   [:form.filter-opts {:action base-path
                       :method "get"}
    [:input {:id "job-type" :type "hidden" :name "job-type" :value job-type}]
    [:div.filter-opts-items
     [:select {:name "filter-type" :class "filter-type"}
      [:option {:value "name" :selected true} "name"]]
     [:div.filter-values
      [:input {:name  "filter-value" :type "text" :placeholder "filter value" :required true
               :class "filter-value" :value filter-value}]]]
    [:div.filter-opts-items
     (when filter-value
       [:button.btn.btn-cancel
        [:a. {:href base-path :class "cursor-default"} "Clear"]])
     [:button.btn {:type "submit"} "Apply"]]]])

(defn- jobs-page-view [data]
  [:div.redis
   [:h1 "Periodic Jobs"]
   [:div.content.redis-jobs-page
    (filter-header data)
    (jobs-table data)]])

(defn validate-get-jobs [{:keys [filter-type filter-value]}]
  {:filter-type (specs/validate-or-default ::specs/periodic-filter-type filter-type)
   :filter-value (specs/validate-or-default ::specs/cron-name filter-value)})

(defn get-jobs [{:keys                                           [prefix-route]
                 {:keys                [app-name]
                  {:keys [redis-conn]} :broker} :console-opts
                 params                         :params}]
  (let [view (console/layout c/header jobs-page-view)
        validated-params (validate-get-jobs params)
        data (data/periodic-page-data redis-conn validated-params)]
    (response/response (view "Periodic" (assoc data :app-name app-name
                                                    :job-type :periodic
                                                    :base-path (prefix-route "/periodic")
                                                    :prefix-route prefix-route
                                                    :params params)))))

(defn delete-jobs [{{{:keys [redis-conn]} :broker} :console-opts
                    :keys                          [prefix-route]
                    params                         :params}]
  (let [{:keys [cron-names]} (specs/validate-req-params params)]
    (apply periodic/delete redis-conn cron-names)
    (response/redirect (prefix-route "/periodic"))))
