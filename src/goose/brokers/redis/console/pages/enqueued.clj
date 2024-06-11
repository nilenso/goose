(ns ^:no-doc goose.brokers.redis.console.pages.enqueued
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.utils :as utils]
            [ring.util.response :as response])
  (:import
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

(defn jobs-table [{:keys [base-path queue jobs]}]
  [:form {:action (str base-path "/jobs")
          :method "post"}
   (c/action-btns [(c/prioritise-btn)
                   (c/delete-btn
                     [:div "Are you sure you want to delete selected jobs in " [:span.highlight queue] " queue?"])])
   [:table.jobs-table
    [:thead
     [:tr
      [:th.id-h "Id"]
      [:th.execute-fn-sym-h "Execute fn symbol"]
      [:th.args-h "Args"]
      [:th.enqueued-at-h "Enqueued at"]
      [:th.checkbox-h [:input {:type "checkbox" :id "checkbox-h"}]]]]
    [:tbody
     (for [{:keys [id execute-fn-sym args enqueued-at] :as j} jobs]
       [:tr
        [:td [:a {:href  (str base-path "/job/" id)
                  :class "underline"}
              [:div.id id]]]
        [:td [:div.execute-fn-sym (str execute-fn-sym)]]
        [:td [:div.args (string/join ", " (mapv c/format-arg args))]]
        [:td [:div.enqueued-at] (Date. ^Long enqueued-at)]
        [:td [:div.checkbox-div
              [:input {:name  "jobs"
                       :type  "checkbox"
                       :class "checkbox"
                       :value (utils/encode-to-str j)}]]]])]]])

(defn- job-page-view [{:keys       [base-path]
                       {:keys [id]
                        :as   job} :job}]
  [:div.redis.redis-enqueued
   [:h1 "Enqueued Job"]
   [:div
    [:form {:action (str base-path "/job/" id)
            :method "post"}
     (c/action-btns [(c/prioritise-btn {:disabled false})
                     (c/delete-btn
                       "Are you sure you want to the delete job?"
                       {:disabled false})])
     [:input {:name  "job"
              :type  "hidden"
              :value (utils/encode-to-str job)}]
     (when job (c/job-table job))]]])

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis.redis-enqueued
   [:h1 "Enqueued Jobs"]
   [:div.content
    (sidebar data)
    [:div.right-side
     (c/filter-header ["id" "execute-fn-sym" "type"] data)
     [:div.pagination
      (when total-jobs
        (c/pagination data))]
     (jobs-table data)
     (when (and total-jobs (> total-jobs 0))
       [:div.bottom
        (c/purge-confirmation-dialog data)
        [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]])]]])

(defn validate-get-jobs [{:keys [page filter-type limit filter-value queue]}]
  (let [page (specs/validate-or-default ::specs/page
                                        (specs/str->long page)
                                        (specs/str->long page)
                                        d/page)
        queue (specs/validate-or-default ::specs/queue queue)
        f-type (specs/validate-or-default ::specs/enqueued-filter-type filter-type)
        f-val (case f-type
                "id" (specs/validate-or-default ::specs/job-id
                                                (parse-uuid filter-value)
                                                filter-value)
                "execute-fn-sym" (specs/validate-or-default ::specs/filter-value-sym
                                                            filter-value)
                "type" (specs/validate-or-default ::specs/filter-value-type
                                                  filter-value)
                nil)
        limit (specs/validate-or-default ::specs/limit
                                         (specs/str->long limit)
                                         (specs/str->long limit)
                                         d/limit)]
    {:page         page
     :queue        queue
     :filter-type  f-type
     :filter-value f-val
     :limit        limit}))

(defn validate-req-params [{:keys [id queue job jobs]}]
  {:id           (specs/validate-or-default ::specs/job-id (-> id str parse-uuid) id)
   :queue        (specs/validate-or-default ::specs/queue queue)
   :encoded-job  (specs/validate-or-default ::specs/encoded-job job job)
   :encoded-jobs (specs/validate-or-default ::specs/encoded-jobs
                                            (specs/->coll jobs)
                                            (specs/->coll jobs))})

(defn get-job [{:keys                          [prefix-route]
                {:keys                [app-name]
                 {:keys [redis-conn]} :broker} :console-opts
                params                         :params}]
  (let [view (console/layout c/header job-page-view)
        {:keys [id queue]} (validate-req-params params)]
    (if id
      (response/response (view "Enqueued" (-> {:job (enqueued-jobs/find-by-id
                                                      redis-conn
                                                      queue
                                                      id)}
                                              (assoc :job-type :enqueued
                                                     :base-path (prefix-route "/enqueued/queue/" queue)
                                                     :app-name app-name
                                                     :prefix-route prefix-route))))
      (response/redirect (prefix-route "/enqueued/queue/" queue)))))

(defn get-jobs [{:keys                     [prefix-route]
                 {:keys [app-name broker]} :console-opts
                 params                    :params}]
  (let [view (console/layout c/header jobs-page-view)
        validated-params (validate-get-jobs params)
        {:keys [queue] :as data} (data/enqueued-page-data (:redis-conn broker) validated-params)]
    (response/response (view "Enqueued" (assoc data :job-type :enqueued
                                                    :base-path (prefix-route "/enqueued/queue/" queue)
                                                    :params params
                                                    :app-name app-name
                                                    :prefix-route prefix-route)))))

(defn purge-queue [{{:keys [broker]} :console-opts
                    params           :params
                    :keys            [prefix-route]}]
  (let [{:keys [queue]} (validate-req-params params)]
    (enqueued-jobs/purge (:redis-conn broker) queue)
    (response/redirect (prefix-route "/enqueued"))))

(defn prioritise-jobs [{{:keys [broker]} :console-opts
                        :keys            [prefix-route]
                        params           :params}]
  (let [{:keys [queue encoded-jobs]} (validate-req-params params)
        jobs (mapv utils/decode-from-str encoded-jobs)]
    (enqueued-jobs/prioritise-execution (:redis-conn broker) queue jobs)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn delete-jobs [{{:keys [broker]} :console-opts
                    :keys            [prefix-route]
                    params           :params}]
  (let [{:keys [queue encoded-jobs]} (validate-req-params params)
        jobs (mapv utils/decode-from-str encoded-jobs)]
    (enqueued-jobs/delete (:redis-conn broker) queue jobs)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn prioritise-job [{:keys                          [prefix-route]
                       {{:keys [redis-conn]} :broker} :console-opts
                       params                         :params}]
  (let [{:keys [queue encoded-job]} (validate-req-params params)
        job (utils/decode-from-str encoded-job)]
    (enqueued-jobs/prioritise-execution redis-conn job)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn delete-job [{:keys                          [prefix-route]
                   {{:keys [redis-conn]} :broker} :console-opts
                   params                         :params}]
  (let [{:keys [queue encoded-job]} (validate-req-params params)
        job (utils/decode-from-str encoded-job)]
    (enqueued-jobs/delete redis-conn job)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))
