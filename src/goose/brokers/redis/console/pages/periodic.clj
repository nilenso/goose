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
     [:th.name "Cron name"]
     [:th.schedule-h "Cron schedule"]
     [:th.timezone "Timezone"]
     [:th.queue-h "Queue"]
     [:th.execute-fn-sym-h "Execute fn symbol"]
     [:th.args-h "Args"]
     [:th.checkbox-h [:input {:type "checkbox" :id "checkbox-h"}]]]
    [:tbody
     (for [{:keys                               [cron-name timezone cron-schedule]
            {:keys [args queue execute-fn-sym]} :job-description} jobs]
       [:tr
        [:td [:a {:href  (str base-path "/job/" cron-name)
                  :class "underline"}
              [:div.name cron-name]]]
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

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis
   [:h1 "Periodic Jobs"]
   [:div.content.redis-jobs-page
    (filter-header data)
    (jobs-table data)
    (when (and total-jobs (> total-jobs 0))
      [:div.bottom
       (console/purge-confirmation-dialog data)
       [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]])]])

(defn job-table [{:keys                                   [cron-name
                                                           cron-schedule
                                                           timezone]
                  {:keys                     [execute-fn-sym
                                              queue
                                              args
                                              ready-queue]
                   {:keys [max-retries
                           retry-delay-sec-fn-sym
                           retry-queue error-handler-fn-sym
                           death-handler-fn-sym
                           skip-dead-queue]} :retry-opts} :job-description}]
  [:table.job-table.table-stripped
   [:tr [:td "Cron name"]
    [:td cron-name]]
   [:tr [:td "Cron schedule"]
    [:td [:div.schedule.blue.tooltip
          cron-schedule
          [:span.tooltip-text
           [:div.tooltip-content
            (CronExpressionDescriptor/getDescription cron-schedule)]]]]]
   [:tr [:td "Timezone"]
    [:td timezone]]
   [:tr [:td "Execute fn symbol"]
    [:td.execute-fn-sym
     (str execute-fn-sym)]]
   [:tr [:td "Args"]
    [:td.args (str/join ", " (mapv console/format-arg args))]]
   [:tr [:td "Ready queue"]
    [:td ready-queue]]
   [:tr [:td "Queue"]
    [:td queue]]
   [:tr [:td "Max retries"]
    [:td max-retries]]
   [:tr [:td "Retry delay sec fn symbol"]
    [:td (str retry-delay-sec-fn-sym)]]
   [:tr [:td "Retry queue"]
    [:td retry-queue]]
   [:tr [:td "Error handler fn symbol"]
    [:td (str error-handler-fn-sym)]]
   [:tr [:td "Death handler fn symbol"]
    [:td (str death-handler-fn-sym)]]
   [:tr [:td "Skip dead queue"]
    [:td skip-dead-queue]]])

(defn- job-page-view [{:keys       [base-path]
                       {:keys [cron-name]
                        :as   job} :job}]
  [:div.redis.redis-enqueued
   [:h1 "Periodic Job"]
   (if job
     [:div
      [:form {:action (str base-path "/job/" cron-name)
              :method "post"}
       [:div
        (c/action-btns [(c/delete-btn
                          "Are you sure you want to delete the job?"
                          {:disabled false})])
        [:input {:name  "cron-name"
                 :type  "hidden"
                 :value cron-name}]
        (job-table job)]]]
     (console/flash-msg {:type    :error
                         :message "No job found"}))])

(defn validate-get-jobs [{:keys [filter-type filter-value]}]
  {:filter-type  (specs/validate-or-default ::specs/periodic-filter-type filter-type)
   :filter-value (specs/validate-or-default ::specs/cron-name filter-value)})

(defn get-jobs [{:keys                          [prefix-route]
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

(defn purge-queue [{{{:keys [redis-conn]} :broker} :console-opts
                    :keys                          [prefix-route]}]
  (periodic/purge redis-conn)
  (response/redirect (prefix-route "/periodic")))

(defn get-job [{:keys                          [prefix-route]
                {:keys                [app-name]
                 {:keys [redis-conn]} :broker} :console-opts
                params                         :params}]
  (let [view (console/layout c/header job-page-view)
        {:keys [cron-name]} (specs/validate-req-params params)
        job (periodic/find-by-name redis-conn cron-name)
        base-response {:job-type     :periodic
                       :base-path    (prefix-route "/periodic")
                       :app-name     app-name
                       :prefix-route prefix-route}]
    (if job
      (response/response (view "Periodic" (assoc base-response :job job)))
      (response/not-found (view "Periodic" base-response)))))

(defn delete-job [{:keys                          [prefix-route]
                   {{:keys [redis-conn]} :broker} :console-opts
                   params                         :params}]
  (let [{:keys [cron-name]} (specs/validate-req-params params)]
    (periodic/delete redis-conn cron-name)
    (response/redirect (prefix-route "/periodic"))))
