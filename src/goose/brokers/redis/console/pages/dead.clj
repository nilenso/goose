(ns goose.brokers.redis.console.pages.dead
  (:require [clojure.string :as string]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.console :as console]
            [goose.defaults :as d]
            [ring.util.response :as response])
  (:import
    (java.util Date)))

(defn validate-get-jobs [{:keys [page filter-type limit filter-value]}]
  (let [page (specs/validate-or-default ::specs/page
                                        (specs/str->long page)
                                        (specs/str->long page)
                                        d/page)
        f-type (specs/validate-or-default ::specs/dead-filter-type filter-type)
        f-val (case f-type
                "id" (specs/validate-or-default ::specs/job-id
                                                (parse-uuid filter-value)
                                                filter-value)
                "execute-fn-sym" (specs/validate-or-default ::specs/filter-value-sym
                                                            filter-value)
                "queue" (specs/validate-or-default ::specs/queue filter-value)
                nil)
        limit (specs/validate-or-default ::specs/limit (specs/str->long limit)
                                         (specs/str->long limit)
                                         d/limit)]
    {:page         page
     :filter-type  f-type
     :filter-value f-val
     :limit        limit}))

(defn jobs-table [{:keys [base-path jobs]}]
  [:form {:action (str base-path "/jobs")
          :method "post"}
   [:table.jobs-table
    [:thead
     [:tr
      [:th.id-h "Id"]
      [:th.queue-h "Queue"]
      [:th.execute-fn-sym-h "Execute fn symbol"]
      [:th.args-h "Args"]
      [:th.enqueued-at-h "Died at"]]]
    [:tbody
     (for [{:keys             [id queue execute-fn-sym args]
            {:keys [died-at]} :state} jobs]
       [:tr
        [:td [:div.id id]]
        [:td [:div.queue] queue]
        [:td [:div.execute-fn-sym (str execute-fn-sym)]]
        [:td [:div.args (string/join ", " (mapv c/format-arg args))]]
        [:td [:div.died-at] (Date. ^Long died-at)]])]]])

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis
   [:h1 "Dead Jobs"]
   [:div.content.redis-jobs-page
    (c/filter-header ["id" "execute-fn-sym" "queue"] data)
    [:div.pagination
     (when total-jobs
       (c/pagination data))]
    (jobs-table data)]])

(defn get-jobs [{:keys                     [prefix-route]
                 {:keys [app-name broker]} :console-opts
                 params                    :params}]
  (let [view (console/layout c/header jobs-page-view)
        validated-params (validate-get-jobs params)
        data (data/dead-page-data (:redis-conn broker) validated-params)]
    (response/response (view "Dead" (assoc data :params params
                                                :job-type :dead
                                                :base-path (prefix-route "/dead")
                                                :app-name app-name
                                                :prefix-route prefix-route)))))
