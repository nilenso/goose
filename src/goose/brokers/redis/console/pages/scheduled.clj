(ns goose.brokers.redis.console.pages.scheduled
  (:require [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.job :as job]
            [goose.utils :as u]
            [ring.util.response :as response])
  (:import
    (java.util Date)))

(defn jobs-table [{:keys [base-path jobs]}]
  [:form {:action (str base-path "/jobs")
          :method "post"}
   [:table.jobs-table
    [:thead
     [:th.when-h [:div.when-label "When"]
      [:label.toggle-switch
       [:input {:type "checkbox" :id "when-option"}]
       [:div.toggle-switch-label
        [:span.in "in"]
        [:span.at "at"]
        [:div.toggle-switch-background]]]
      [:th.id-h "Id"]
      [:th.queue-h "Queue"]
      [:th.execute-fn-sym-h "Execute fn symbol"]
      [:th.type-h "Type"]]]
    [:tbody
     (for [{:keys [id queue execute-fn-sym schedule-run-at] :as j} jobs]
       (let [relative-time (when schedule-run-at (u/relative-time schedule-run-at))
             absolute-time (when schedule-run-at (Date. ^Long schedule-run-at))]
         [:tr
          [:td.when
           [:div.schedule-run-at-rel-time relative-time]
           [:div.schedule-run-at-abs-time {:class "invisible"} absolute-time]]
          [:td [:div.id id]]
          [:td [:div.queue] queue]
          [:td [:div.execute-fn-sym (str execute-fn-sym)]]
          [:td.type (if (job/retried? j) "Retrying" "Scheduled")]]))]]])

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis
   [:h1 "Scheduled Jobs"]
   [:div.content.redis-jobs-page
    (c/filter-header ["id" "execute-fn-sym" "queue" "type"] data)
    [:div.pagination
     (when total-jobs
       (c/pagination data))]
    (jobs-table data)]])

(defn validate-get-jobs [{:keys [page filter-type filter-value limit]}]
  (let [f-type (specs/validate-or-default ::specs/scheduled-filter-type filter-type)]
    {:page         (specs/validate-or-default ::specs/page
                                              (specs/str->long page)
                                              (specs/str->long page)
                                              d/page)
     :filter-type f-type
     :filter-value (case f-type
                     "id" (specs/validate-or-default ::specs/job-id
                                                     (parse-uuid filter-value)
                                                     filter-value)
                     "execute-fn-sym" (specs/validate-or-default ::specs/filter-value-sym
                                                                 filter-value)
                     "queue" (specs/validate-or-default ::specs/queue filter-value)
                     "type" (specs/validate-or-default ::specs/filter-value-type filter-value)
                     nil)
     :limit        (specs/validate-or-default ::specs/limit
                                              (specs/str->long limit)
                                              (specs/str->long limit)
                                              d/limit)}))

(defn get-jobs [{:keys                          [prefix-route]
                 {:keys                [app-name]
                  {:keys [redis-conn]} :broker} :console-opts
                 params                         :params}]
  (let [view (console/layout c/header jobs-page-view)
        validated-params (validate-get-jobs params)
        data (data/scheduled-page-data redis-conn validated-params)]
    (response/response (view "Scheduled" (assoc data :app-name app-name
                                                     :job-type :scheduled
                                                     :base-path (prefix-route "/scheduled")
                                                     :prefix-route prefix-route
                                                     :params params)))))
