(ns goose.brokers.redis.console.pages.components
  (:require [clojure.string :as str]
            [goose.job :as job]
            [hiccup.page :refer [html5 include-css include-js]])
  (:import
    (java.lang Character String)
    (java.util Date)))

(defn format-arg [arg]
  (condp = (type arg)
    String (str "\"" arg "\"")
    nil "nil"
    Character (str "\\" arg)
    arg))

(defn layout [& components]
  (fn [title {:keys [prefix-route] :as data}]
    (html5 {:lang "en"}
           [:head
            [:meta {:charset "UTF-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
            [:title title]
            (include-css (prefix-route "/css/style.css"))
            (include-js (prefix-route "/js/index.js"))]
           [:body
            (map (fn [c] (c data)) components)])))

(defn header [{:keys [app-name prefix-route uri] :or {app-name ""}}]
  (let [subroute? (fn [r] (str/includes? uri (prefix-route r)))
        short-app-name (if (> (count app-name) 20)
                         (str (subs app-name 0 17) "..")
                         app-name)]
    [:header
     [:nav
      [:div.nav-start
       [:div.goose-logo
        [:a {:href ""}
         [:img {:src (prefix-route "/img/goose-logo.png") :alt "goose-logo"}]]]
       [:div#menu
        [:a {:href (prefix-route "/") :class "app-name"} short-app-name]
        [:a {:href (prefix-route "/enqueued")
             :class (when (subroute? "/enqueued") "highlight")} "Enqueued"]]]]]))

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
