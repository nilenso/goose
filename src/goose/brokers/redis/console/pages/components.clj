(ns ^:no-doc goose.brokers.redis.console.pages.components
  (:require [clojure.math :as math]
            [clojure.string :as str]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.job :as job]
            [hiccup.util :as hiccup-util])
  (:import
    (java.lang Character String)
    (java.util Date)))

(defn format-arg [arg]
  (condp = (type arg)
    String (str "\"" arg "\"")
    nil "nil"
    Character (str "\\" arg)
    arg))

(def header
  (partial console/header [{:route "/enqueued" :label "Enqueued" :job-type :enqueued}
                           {:route "/dead" :label "Dead" :job-type :dead}]))

(defn delete-confirm-dialog [question]
  [:dialog {:class "delete-dialog"}
   [:div question]
   [:div.dialog-btns
    [:input.btn.btn-md.btn-cancel {:type "button" :value "cancel" :class "cancel"}]
    [:input.btn.btn-md.btn-danger {:type "submit" :name "_method" :value "delete"}]]])

(defn purge-confirmation-dialog [{:keys [total-jobs base-path]}]
  [:dialog {:class "purge-dialog"}
   [:div "Are you sure, you want to remove " [:span.highlight total-jobs] " jobs ?"]
   [:form {:action base-path
           :method "post"
           :class  "dialog-btns"}
    [:input {:name "_method" :type "hidden" :value "delete"}]
    [:input {:type "button" :value "Cancel" :class "btn btn-md btn-cancel cancel"}]
    [:input {:type "submit" :value "Confirm" :class "btn btn-danger btn-md"}]]])

(defn replay-btn [& {:keys [disabled] :or {disabled true}}]
  [:input.btn {:type "submit" :value "Replay" :disabled disabled}])

(defn prioritise-btn [& {:keys [disabled] :or {disabled true}}]
  [:input.btn {:type "submit" :value "Prioritise" :disabled disabled}])

(defn delete-btn [question & {:keys [disabled] :or {disabled true}}]
  [:div (delete-confirm-dialog question)
   [:input.btn.btn-danger
    {:type "button" :value "Delete" :class "delete-dialog-show" :disabled disabled}]])

(defn action-btns [btns]
  [:div.actions
   (for [btn btns]
     btn)])

(defn pagination-stats [first-page curr-page last-page]
  {:first-page first-page
   :prev-page  (dec curr-page)
   :curr-page  curr-page
   :next-page  (inc curr-page)
   :last-page  last-page})

(defn pagination [{:keys [page total-jobs base-path]}]
  (let [{:keys [first-page prev-page curr-page
                next-page last-page]} (pagination-stats d/page page
                                                        (int (math/ceil (/ total-jobs d/page-size))))
        page-uri (fn [p] (str base-path "?page=" p))
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

(defn filter-header [filter-types {:keys                                    [base-path]
                                   {:keys [filter-type filter-value limit]} :params}]
  [:div.header
   [:form.filter-opts {:action base-path
                       :method "get"}
    [:div.filter-opts-items
     [:select {:name "filter-type" :class "filter-type"}
      (for [type filter-types]
        [:option {:value type :selected (= type filter-type)} type])]
     [:div.filter-values

      ;; filter-value element is dynamically changed in JavaScript based on filter-type
      ;; Any attribute update in field-value should be reflected in JavaScript file too

      (if (= filter-type "type")
        [:select {:name "filter-value" :class "filter-value" :required true}
         (for [val ["unexecuted" "failed"]]
           [:option {:value val :selected (= val filter-value)} val])]
        [:input {:name  "filter-value" :type "text" :placeholder "filter value" :required true
                 :class "filter-value" :value filter-value}])]]
    [:div.filter-opts-items
     [:span.limit "Limit"]
     [:input {:type  "number" :name "limit" :id "limit" :placeholder "custom limit"
              :value (if (str/blank? limit) d/limit limit)
              :min   "0"
              :max   "10000"}]]
    [:div.filter-opts-items
     [:button.btn.btn-cancel
      [:a. {:href base-path :class "cursor-default"} "Clear"]]
     [:button.btn {:type "submit"} "Apply"]]]])

(defn job-table [{:keys                     [id execute-fn-sym args queue ready-queue enqueued-at]
                  {:keys [max-retries
                          retry-delay-sec-fn-sym
                          retry-queue error-handler-fn-sym
                          death-handler-fn-sym
                          skip-dead-queue]} :retry-opts
                  {:keys [error
                          last-retried-at
                          first-failed-at
                          retry-count
                          retry-at]}        :state
                  :as                       job}]
  [:table.job-table.table-stripped
   [:tr [:td "Id"]
    [:td id]]
   [:tr [:td "Execute fn symbol"]
    [:td.execute-fn-sym
     (str execute-fn-sym)]]
   [:tr [:td "Args"]
    [:td.args (str/join ", " (mapv format-arg args))]]
   [:tr [:td "Queue"]
    [:td queue]]
   [:tr [:td "Ready queue"]
    [:td ready-queue]]
   [:tr [:td "Enqueued at"]
    [:td (Date. ^Long enqueued-at)]]
   [:tr [:td "Max retries"]
    [:td max-retries]]
   [:tr [:td "Retry delay sec fn symbol"]
    [:td retry-delay-sec-fn-sym]]
   [:tr [:td "Retry queue"]
    [:td retry-queue]]
   [:tr [:td "Error handler fn symbol"]
    [:td error-handler-fn-sym]]
   [:tr [:td "Death handler fn symbol"]
    [:td death-handler-fn-sym]]
   [:tr [:td "Skip dead queue"]
    [:td skip-dead-queue]]
   (when (job/retried? job)
     [:div
      [:tr [:td "Error"]
       [:td error]]
      [:tr [:td "Last retried at"]
       [:td (Date. ^Long last-retried-at)]]
      [:tr [:td "First failed at"]
       [:td (Date. ^Long first-failed-at)]]
      [:tr [:td "Retry count"]
       [:td retry-count]]
      [:tr [:td "Retry at"]
       [:td (Date. ^Long retry-at)]]])])
