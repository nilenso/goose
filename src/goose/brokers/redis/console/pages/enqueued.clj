(ns goose.brokers.redis.console.pages.enqueued
  (:require [clojure.string :as string]
            [goose.brokers.redis.api.enqueued-jobs :as enqueued-jobs]
            [goose.brokers.redis.console.data :as data]
            [goose.brokers.redis.console.pages.components :as c]
            [goose.brokers.redis.console.specs :as specs]
            [goose.defaults :as d]
            [goose.job :as job]
            [goose.utils :as utils]
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

(defn- purge-confirmation-dialog [{:keys [prefix-route queue]}]
  [:dialog {:class "purge-dialog"}
   [:div "Are you sure, you want to " [:b "purge "] "the " [:span.highlight queue] " queue?"]
   [:form {:action (prefix-route "/enqueued/queue/" queue)
           :method "post"
           :class  "dialog-btns"}
    [:input {:name "_method" :type "hidden" :value "delete"}]
    [:input {:type "button" :value "Cancel" :class "btn btn-md btn-cancel cancel"}]
    [:input {:type "submit" :value "Confirm" :class "btn btn-danger btn-md"}]]])

(defn- sticky-header [{:keys                                    [prefix-route queue]
                       {:keys [filter-type filter-value limit]} :params}]
  [:div.header
   [:form.filter-opts {:action (prefix-route "/enqueued/queue/" queue)
                       :method "get"}
    [:div.filter-opts-items
     [:select {:name "filter-type" :class "filter-type"}
      (for [type ["id" "execute-fn-sym" "type"]]
        [:option {:value type :selected (= type filter-type)} type])]
     [:div.filter-values

      ;; filter-value element is dynamically changed in JavaScript based on filter-type
      ;; Any attribute update in field-value should be reflected in JavaScript file too

      (if (= filter-type "type")
        [:select {:name "filter-value" :class "filter-value"}
         (for [val ["unexecuted" "failed"]]
           [:option {:value val :selected (= val filter-value)} val])]
        [:input {:name  "filter-value" :type "text" :placeholder "filter value"
                 :class "filter-value" :value filter-value}])]]
    [:div.filter-opts-items
     [:span.limit "Limit"]
     [:input {:type  "number" :name "limit" :id "limit" :placeholder "custom limit"
              :value (if (string/blank? limit) d/limit limit)
              :max   "10000"}]]
    [:div.filter-opts-items
     [:button.btn.btn-cancel
      [:a. {:href (prefix-route "/enqueued/queue/" queue) :class "cursor-default"} "Clear"]]
     [:button.btn {:type "submit"} "Apply"]]]])

(defn- delete-confirmation-dialog [queue]
  [:dialog {:class "delete-dialog"}
   [:div "Are you sure, you want to " [:b "delete"] " jobs from " [:span.highlight queue] " queue?"]
   [:div {:class "dialog-btns"}
    [:input {:type "button" :value "cancel" :class "btn btn-md btn-cancel cancel"}]
    [:input.btn.btn-danger.btn-md {:type "submit" :name "_method" :value "delete"}]]])

(defn jobs-table [{:keys [prefix-route queue jobs]}]
  [:form {:action (prefix-route "/enqueued/queue/" queue "/jobs")
          :method "post"}
   (delete-confirmation-dialog queue)
   [:div.actions
    [:input {:name "queue" :value queue :type "hidden"}]
    [:input.btn {:type "submit" :value "Prioritise" :disabled true}]
    [:input.btn.btn-danger
     {:type "button" :value "Delete" :class "delete-dialog-show" :disabled true}]]
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
        [:td [:div.id id]]
        [:td [:div.execute-fn-sym (str execute-fn-sym)]]
        [:td [:div.args (string/join ", " args)]]
        [:td [:div.enqueued-at] (Date. ^Long enqueued-at)]
        [:td [:div.checkbox-div
              [:input {:name  "jobs"
                       :type  "checkbox"
                       :class "checkbox"
                       :value (utils/encode-to-str j)}]]]])]]])

(defn- delete-job-confirm-dialog [id]
  [:dialog {:class "delete-dialog"}
   [:div "Are you sure, you want to delete job " [:b id] " ?"]
   [:div.dialog-btns
    [:input.btn.btn-md.btn-cancel {:type "button" :value "cancel" :class "cancel"}]
    [:input.btn.btn-md.btn-danger {:type "submit" :name "_method" :value "delete"}]]])

(defn- job-table [job]
  [:table.job-table
   [:tr
    [:td "Id"]
    [:td (:id job)]]
   [:tr
    [:td "Execute fn symbol"]
    [:td (str (:execute-fn-sym job))]]
   [:tr
    [:td "Args"]
    [:td (string/join ", " (:args job))]]
   [:tr
    [:td "Queue"]
    [:td (:queue job)]]
   [:tr
    [:td "Ready queue"]
    [:td (:ready-queue job)]]
   [:tr
    [:td "Enqueued at"]
    [:td (Date. ^Long (:enqueued-at job))]]
   [:tr
    [:td "Max retries"]
    [:td (get-in job [:retry-opts :max-retries])]]
   [:tr
    [:td "Retry delay sec fn symbol"]
    [:td (str (get-in job [:retry-opts :retry-delay-sec-fn-sym]))]]
   [:tr
    [:td "Retry queue"]
    [:td (get-in job [:retry-opts :retry-delay-sec-fn-sym])]]
   [:tr
    [:td "Error handler fn symbol"]
    [:td (str (get-in job [:retry-opts :error-handler-fn-sym]))]]
   [:tr
    [:td "Death handler fn symbol"]
    [:td (str (get-in job [:retry-opts :death-handler-fn-sym]))]]
   [:tr
    [:td "Skip dead queue"]
    [:td (get-in job [:retry-opts :skip-dead-queue])]]
   (when (job/retried? job)
     [:div
      [:tr
       [:td "Error"]
       [:td (get-in job [:state :error])]]
      [:tr
       [:td "Last retried at"]
       [:td (Date. ^Long (get-in job [:state :last-retried-at]))]]
      [:tr
       [:td "First failed at"]
       [:td (Date. ^Long (get-in job [:state :first-failed-at]))]]
      [:tr
       [:td "Retry count"]
       [:td (get-in job [:state :retry-count])]]
      [:tr
       [:td "Retry at"]
       [:td (Date. ^Long (get-in job [:state :retry-at]))]]])])

(defn- job-page-view [{:keys [prefix-route queue]
                       {:keys [id]
                        :as job} :job}]
  [:div.redis-enqueued-main-content
   [:h1 "Enqueued Job"]
   [:div
    [:form {:action (prefix-route "/enqueued/queue/" queue "/job/" id)
            :method "post"}
     (delete-job-confirm-dialog id)
     [:div.actions
      [:input.btn {:type "submit" :value "Prioritise"}]
      [:input.btn.btn-danger
       {:type "button" :value "Delete" :class "delete-dialog-show"}]
      [:input {:name  "job"
               :type "hidden"
               :value (utils/encode-to-str job)}]]

     [:table
      (when job (job-table job))]]]])

(defn- jobs-page-view [{:keys [total-jobs] :as data}]
  [:div.redis-enqueued-main-content
   [:h1 "Enqueued Jobs"]
   [:div.content
    (sidebar data)
    [:div.right-side
     (sticky-header data)
     [:div.pagination
      (when total-jobs
        (pagination data))]
     (jobs-table data)
     (when (and total-jobs (> total-jobs 0))
       [:div.bottom
        (purge-confirmation-dialog data)
        [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]])]]])

(defn validate-get-jobs [{:keys [page filter-type limit filter-value queue]}]
  (let [page (specs/validate-or-default ::specs/page
                                        (specs/str->long page)
                                        (specs/str->long page)
                                        d/page)
        queue (specs/validate-or-default ::specs/queue queue)
        f-type (specs/validate-or-default ::specs/filter-type filter-type)
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

(defn validate-jobs [jobs]
  (if (sequential? jobs)
    jobs
    (conj [] jobs)))

(defn validate-job-params [{:keys [id queue]}]
  {:id    (specs/validate-or-default ::specs/job-id (parse-uuid id) id)
   :queue (specs/validate-or-default ::specs/queue queue)})

(defn job-page [{:keys                          [prefix-route]
                 {:keys                [app-name]
                  {:keys [redis-conn]} :broker} :console-opts
                 params                         :params}]
  (let [view (c/layout c/header job-page-view)
        {:keys [id queue]} (validate-job-params params)]
    (if id
      (response/response (view "Enqueued" (-> {:job (enqueued-jobs/find-by-id
                                                      redis-conn
                                                      queue
                                                      id)}
                                              (assoc :queue queue
                                                     :app-name app-name
                                                     :prefix-route prefix-route))))
      (response/redirect (prefix-route "/enqueued/queue/" queue)))))

(defn get-jobs [{:keys                     [prefix-route]
                 {:keys [app-name broker]} :console-opts
                 params                    :params}]
  (let [view (c/layout c/header jobs-page-view)
        validated-params (validate-get-jobs params)
        data (data/enqueued-page-data (:redis-conn broker) validated-params)]
    (response/response (view "Enqueued" (assoc data :params params
                                                    :app-name app-name
                                                    :prefix-route prefix-route)))))

(defn purge-queue [{{:keys [broker]} :console-opts
                    {:keys [queue]}  :params
                    :keys            [prefix-route]}]
  (enqueued-jobs/purge (:redis-conn broker) queue)
  (response/redirect (prefix-route "/enqueued")))

(defn prioritise-jobs [{{:keys [broker]}     :console-opts
                        :keys                [prefix-route]
                        {:keys [queue jobs]} :params}]
  (let [jobs (->> jobs
                  validate-jobs
                  (mapv utils/decode-from-str))]
    (enqueued-jobs/prioritise-execution (:redis-conn broker) queue jobs)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn delete-jobs [{{:keys [broker]}     :console-opts
                    :keys                [prefix-route]
                    {:keys [queue jobs]} :params}]
  (let [jobs (->> jobs
                  validate-jobs
                  (mapv utils/decode-from-str))]
    (enqueued-jobs/delete (:redis-conn broker) queue jobs)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn prioritise-job [{:keys                          [prefix-route]
                       {{:keys [redis-conn]} :broker} :console-opts
                       {:keys [queue]
                        encoded-job :job}           :params}]
  (let [job (utils/decode-from-str encoded-job)]
    (enqueued-jobs/prioritise-execution redis-conn job)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))

(defn delete-job [{:keys                          [prefix-route]
                       {{:keys [redis-conn]} :broker} :console-opts
                       {:keys [queue]
                        encoded-job :job}           :params}]
  (let [job (utils/decode-from-str encoded-job)]
    (enqueued-jobs/delete redis-conn job)
    (response/redirect (prefix-route "/enqueued/queue/" queue))))
