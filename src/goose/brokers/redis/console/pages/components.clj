(ns ^:no-doc goose.brokers.redis.console.pages.components
  (:require [clojure.math :as math]
            [clojure.string :as str]
            [goose.console :as console]
            [goose.defaults :as d]
            [hiccup.util :as hiccup-util]))

(def header
  (partial console/header [{:route "/enqueued" :label "Enqueued" :job-type :enqueued}
                           {:route "/scheduled" :label "Scheduled" :job-type :scheduled}
                           {:route "/dead" :label "Dead" :job-type :dead}]))

(defn replay-btn [& {:keys [disabled] :or {disabled true}}]
  [:input.btn {:type "submit" :value "Replay" :disabled disabled}])

(defn prioritise-btn [& {:keys [disabled] :or {disabled true}}]
  [:input.btn {:type "submit" :value "Prioritise" :disabled disabled}])

(defn delete-btn [question & {:keys [disabled] :or {disabled true}}]
  [:div (console/delete-confirm-dialog question)
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
