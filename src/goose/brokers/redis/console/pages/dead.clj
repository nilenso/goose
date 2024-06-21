(ns goose.brokers.redis.console.pages.dead
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.dead-jobs :as dead-jobs]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.utils :as utils]
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
   (c/action-btns [(c/replay-btn)
                   (c/delete-btn
                     [:div "Are you sure you want to delete selected jobs?"])])
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
        [:td [:a {:href  (str base-path "/job/" id)
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
    (c/filter-header ["id" "execute-fn-sym" "queue"] data)
    [:div.pagination
     (when total-jobs
       (c/pagination data))]
    (jobs-table data)
    (when (and total-jobs (> total-jobs 0))
      [:div.bottom
       (c/purge-confirmation-dialog data)
       [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]])]])

(defn- job-page-view [{:keys       [base-path]
                       {:keys [id]
                        :as   job} :job}]
  [:div.redis
   [:h1 "Dead Job"]
   (if job
     [:div
      [:form {:action (str base-path "/job/" id)
              :method "post"}
       [:div
        (c/action-btns [(c/replay-btn {:disabled false})
                        (c/delete-btn
                          "Are you sure you want to delete the job?"
                          {:disabled false})])
        [:input {:name  "job"
                 :type  "hidden"
                 :value (utils/encode-to-str job)}]
        (c/job-table job)]]]
     (c/flash-msg {:type    :error
                   :message "No job found"}))])

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

(defn purge-queue [{{{:keys [redis-conn]} :broker} :console-opts
                    :keys                          [prefix-route]}]
  (dead-jobs/purge redis-conn)
  (response/redirect (prefix-route "/dead")))

(defn replay-jobs [{{{:keys [redis-conn]} :broker} :console-opts
                    :keys                          [prefix-route]
                    params                         :params}]
  (let [{:keys [encoded-jobs]} (specs/validate-req-params params)
        jobs (mapv utils/decode-from-str encoded-jobs)]
    (apply dead-jobs/replay-jobs redis-conn jobs)
    (response/redirect (prefix-route "/dead"))))

(defn delete-jobs [{{{:keys [redis-conn]} :broker} :console-opts
                    :keys                          [prefix-route]
                    params                         :params}]
  (let [{:keys [encoded-jobs]} (specs/validate-req-params params)
        jobs (mapv utils/decode-from-str encoded-jobs)]
    (apply dead-jobs/delete redis-conn jobs)
    (response/redirect (prefix-route "/dead"))))

(defn get-job [{:keys                          [prefix-route]
                {:keys                [app-name]
                 {:keys [redis-conn]} :broker} :console-opts
                params                         :params}]
  (let [view (console/layout c/header job-page-view)
        {:keys [id]} (specs/validate-req-params params)
        base-response {:job-type     :dead
                       :base-path    (prefix-route "/dead")
                       :app-name     app-name
                       :prefix-route prefix-route}]
    (if id
      (if-let [job (dead-jobs/find-by-id redis-conn id)]
        (response/response (view "Dead" (assoc base-response :job job)))
        (response/not-found (view "Dead" base-response)))
      (response/redirect (prefix-route "/dead")))))

(defn replay-job [{:keys                          [prefix-route]
                   {{:keys [redis-conn]} :broker} :console-opts
                   params                         :params}]
  (let [{:keys [encoded-job]} (specs/validate-req-params params)
        job (utils/decode-from-str encoded-job)]
    (dead-jobs/replay-job redis-conn job)
    (response/redirect (prefix-route "/dead"))))

(defn delete-job [{:keys                          [prefix-route]
                   {{:keys [redis-conn]} :broker} :console-opts
                   params                         :params}]
  (let [{:keys [encoded-job]} (specs/validate-req-params params)
        job (utils/decode-from-str encoded-job)]
    (dead-jobs/delete redis-conn job)
    (response/redirect (prefix-route "/dead"))))
