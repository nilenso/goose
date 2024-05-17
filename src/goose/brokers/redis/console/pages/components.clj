(ns goose.brokers.redis.console.pages.components
  (:require [clojure.string :as str]
            [goose.console :as console]
            [goose.job :as job])
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
  (partial console/header [{:route "/enqueued" :label "Enqueued"}]))

(defn delete-confirm-dialog [question]
  [:dialog {:class "delete-dialog"}
   [:div question]
   [:div.dialog-btns
    [:input.btn.btn-md.btn-cancel {:type "button" :value "cancel" :class "cancel"}]
    [:input.btn.btn-md.btn-danger {:type "submit" :name "_method" :value "delete"}]]])

(defn action-btns [& {:keys [disabled] :or {disabled true}}]
  [:div.actions
   [:input.btn {:type "submit" :value "Prioritise" :disabled disabled}]
   [:input.btn.btn-danger
    {:type "button" :value "Delete" :class "delete-dialog-show" :disabled disabled}]])

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
    [:td.blue id]]
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
