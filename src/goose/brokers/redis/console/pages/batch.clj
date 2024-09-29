(ns goose.brokers.redis.console.pages.batch
  (:require [goose.brokers.redis.api.batch :as batch-jobs]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.console :as console]
            [goose.utils :as utils]
            [ring.util.response :as response])
  (:import
   (java.util Date)))
(defn- batch-job-table
  [{:keys                     [id
                               callback-fn-sym
                               dead
                               enqueued
                               success
                               total
                               retrying
                               queue
                               ready-queue
                               status
                               created-at]
    {:keys [max-retries
            retry-delay-sec-fn-sym
            retry-queue error-handler-fn-sym
            death-handler-fn-sym
            skip-dead-queue]} :retry-opts}]
  [:table.job-table.table-stripped
   [:tr [:td "Id"]
    [:td id]]
   [:tr [:td "Callback fn symbol"]
    [:td.execute-fn-sym
     (str callback-fn-sym)]]
   [:tr [:td "Ready queue"]
    [:td ready-queue]]
   [:tr [:td "Queue"]
    [:td queue]]
   [:tr [:td "Status"]
    [:td status]]
   (when created-at
     [:tr [:td "Created at"]
      [:td (Date. ^Long created-at)]])
   [:tr [:td "Total"]
    [:td total]]
   [:tr [:td "Enqueued"]
    [:td enqueued]]
   [:tr [:td "Success"]
    [:td success]]
   [:tr [:td "Retrying"]
    [:td retrying]]
   [:tr [:td "Dead"]
    [:td dead]]
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

(defn job-table [{:keys [base-path job]}]
  [:div
   [:form {:action (str base-path "/job/" (get job :id))
           :method "post"}
    [:div.padding-top
     [:input {:name  "job"
              :type  "hidden"
              :value (utils/encode-to-str job)}]
     (c/action-btns [(c/delete-btn
                      [:div "Are you sure you want to delete "
                       [:span.highlight (get job :total)] " job/s?"]
                      {:disabled false})])
     (batch-job-table job)]]])

(defn- filter-header [{:keys                  [base-path job-type]
                       {:keys [filter-value]} :params}]
  [:form.filter-opts {:action base-path
                      :method "get"}
   [:input {:id "job-type" :type "hidden" :name "job-type" :value job-type}]
   [:div.filter-opts-items
    [:select {:name "filter-type" :class "filter-type"}
     [:option {:value "id" :selected true} "id"]]
    [:div.filter-values
     [:input {:name  "filter-value" :type "text" :placeholder "filter value" :required true
              :class "filter-value" :value filter-value}]]]
   [:div.filter-opts-items
    (when filter-value
      [:button.btn.btn-cancel
       [:a. {:href base-path :class "cursor-default"} "Clear"]])
    [:button.btn {:type "submit"} "Apply"]]])

(defn job-page-view [{:keys [job]
                      :as   data}]
  [:div.redis {:id "page"}
   [:h1 "Batch Job"]
   [:div.content.redis-jobs-page
    [:div.header
     (filter-header data)]
    (if job
      (job-table data)
      [:div {:class "padding-top"}
       (console/flash-msg {:type    :info
                           :message "Search batch id to view batch job's metadata"})])]])

(defn validate-get-jobs [{:keys [filter-type filter-value]}]
  (let [f-type (specs/validate-or-default ::specs/batch-filter-type filter-type)
        f-val (when f-type
                "id" (specs/validate-or-default ::specs/job-id (parse-uuid filter-value) filter-value))]
    {:filter-type  f-type
     :filter-value f-val}))

(defn get-job [{:keys                          [prefix-route]
                {:keys                [app-name]
                 {:keys [redis-conn]} :broker} :console-opts
                params                         :params}]
  (let [view (console/layout c/header job-page-view)
        {id :filter-value} (validate-get-jobs params)
        base-response {:job-type     :batch
                       :base-path    (prefix-route "/batch")
                       :app-name     app-name
                       :prefix-route prefix-route
                       :params       params}]
    (if id
      (if-let [job (batch-jobs/status redis-conn id)]
        (response/response (view "Batch" (assoc base-response :job job)))
        (response/not-found (view "Batch" base-response)))
      (response/response (view "Batch" base-response)))))

(defn delete-job [{:keys                          [prefix-route]
                   {{:keys [redis-conn]} :broker} :console-opts
                   params                         :params}]
  (let [{:keys [id]} (specs/validate-req-params params)]
    (when id
      (batch-jobs/delete redis-conn id))
    (response/redirect (prefix-route "/batch"))))
