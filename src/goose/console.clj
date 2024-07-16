(ns goose.console
  "Functions to serve the console web interface"
  (:require [clojure.string :as str]
            [goose.broker :as b]
            [goose.defaults :as d]
            [goose.job :as job]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]
            [ring.util.response :as response])
  (:import
    (java.lang Character String)
    (java.util Date)))

(defn format-arg [arg]
  (condp = (type arg)
    String (str "\"" arg "\"")
    nil "nil"
    Character (str "\\" arg)
    arg))

;; Page handlers
(defn ^:no-doc load-css
  "Load goose console's static css file"
  [_]
  (-> "css/style.css"
      response/resource-response
      (response/header "Content-Type" "text/css")))

(defn ^:no-doc load-img
  "Load goose logo"
  [_]
  (-> "img/goose-logo.png"
      response/resource-response
      (response/header "Content-Type" "image/png")))

(defn ^:no-doc load-js
  "Load goose console's javascript file"
  [_]
  (-> "js/index.js"
      response/resource-response
      (response/header "Content-Type" "text/javascript")))

(defn ^:no-doc redirect-to-home-page
  "Redirects the user to the homepage"
  [{:keys [prefix-route]}]
  (response/redirect (prefix-route "/")))

(defn ^:no-doc not-found
  "Default not found response"
  [_]
  (response/not-found "<div> Not found </div>"))

;; View components
(defn ^:no-doc layout
  "Generates HTML layout for webpage using provided hiccup components

   ### Args

   components: A variadic list of functions. Each function is expected to accept
   a map `data` and return hiccup data representing part of a webpage

   ### Usage
   ```clojure
    (defn job [j]
      [:div (:id j)])

   (defn job-page [{:keys [id] :as _req}]
     (let [view (layout job)]
       (view \"Jobs page\" {:id id :prefix-route str})))
       ```"
  [& components]
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

(defn ^:no-doc delete-confirm-dialog [question]
  [:dialog {:class "delete-dialog"}
   [:div question]
   [:div.dialog-btns
    [:input.btn.btn-md.btn-cancel {:type "button" :value "cancel" :class "cancel"}]
    [:input.btn.btn-md.btn-danger {:type "submit" :name "_method" :value "delete"}]]])

(defn ^:no-doc purge-confirmation-dialog [{:keys [total-jobs base-path]}]
  [:dialog {:class "purge-dialog"}
   [:div "Are you sure, you want to remove " [:span.highlight total-jobs] " jobs ?"]
   [:form {:action base-path
           :method "post"
           :class  "dialog-btns"}
    [:input {:name "_method" :type "hidden" :value "delete"}]
    [:input {:type "button" :value "Cancel" :class "btn btn-md btn-cancel cancel"}]
    [:input {:type "submit" :value "Confirm" :class "btn btn-danger btn-md"}]]])

(defn ^:no-doc flash-msg [{:keys [type message class]}]
  (let [append-class #(str/join " " %)]
    (case type
      :success [:div.flash-success {:class (append-class class)} message]
      :error [:div.flash-error {:class (append-class class)} message]
      :info [:div.flash-info {:class (append-class class)} message]
      [:div {:class (append-class class)} message])))

(defn ^:no-doc job-table [{:keys                     [id execute-fn-sym args queue ready-queue enqueued-at]
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
       [:td (when last-retried-at (Date. ^Long last-retried-at))]]
      [:tr [:td "First failed at"]
       [:td (when first-failed-at (Date. ^Long first-failed-at))]]
      [:tr [:td "Retry count"]
       [:td retry-count]]
      [:tr [:td "Retry at"]
       [:td (when retry-at (Date. ^Long retry-at))]]])])

(defn ^:no-doc header
  "Creates a navbar header in hiccup syntax consisting of goose logo and app name

   ### Args

  `header-items` : A seq of maps each containing route and label keys to
   specify navigation links

   `data` : A map that includes:

   - `uri`         : The current page's URI to check to highlight as active link
   - `app-name`    : Application's name
   - `prefix-route`: Function to prepend paths to generate url"

  [header-items {:keys [app-name prefix-route job-type] :or {app-name ""}
                 :as   _data}]
  (let [short-app-name (if (> (count app-name) 20)
                         (str (subs app-name 0 17) "..")
                         app-name)]
    [:header
     [:nav
      [:div.nav-start
       [:div.goose-logo
        [:a {:href (prefix-route "/")}
         [:img {:src (prefix-route "/img/goose-logo.png") :alt "goose-logo"}]]]
       [:div#menu
        [:a {:href (prefix-route "/") :class "app-name"} short-app-name]
        (for [{:keys [route label]
               type  :job-type} header-items]
          [:a {:href  (prefix-route route)
               :class (when (= job-type type) "highlight")} label])]]]]))

(defn ^:no-doc wrap-prefix-route
  "Middleware that injects a `prefix-route` function into the request,
  that facilitates URL construction in views by prepending given route-prefix to paths"
  [handler]
  (fn [{{:keys [route-prefix]} :console-opts
        :as                    req}]
    (let [prefix-route-fn (partial str route-prefix)]
      (handler (assoc req :prefix-route prefix-route-fn)))))

(defn ^:no-doc wrap-method-override
  "Middleware to override HTTP method based on the `_method` parameters in request params, allowing
   to simulate PUT, DELETE, PATCH etc. methods that are not supported in HTML forms"
  [handler]
  (fn [request]
    (if-let [overridden-method (get-in request [:params :_method])]
      (handler (assoc request :request-method (keyword overridden-method)))
      (handler request))))

(def default-console-opts
  "Map of sample configs for app-handler.

  ### Keys

  `:broker`       : Message broker that transfers message from Producer to Consumer.\\
   Given value must implement [[goose.broker/Broker]] protocol. \\
   [Message Broker wiki](https://github.com/nilenso/goose/wiki/Message-Brokers)

  `:route-prefix` : Route path that exposes console via app-handler (should not include a trailing \"/\") \\
    *Example*     : [[goose.defaults/route-prefix]] 

  `:app-name`     : Name to be displayed in console navbar \\
    *Example*     : [[goose.defaults/app-name]] "
  {:route-prefix d/route-prefix
   :app-name     d/app-name})

(defn app-handler
  "A Ring handler that sets up the necessary middleware and serves Goose Console.

   It takes two arguments:

   ### Args

   `:console-opts`   : A map of `:broker`, `:route-prefix`, & `app-name` \\
    *Example*        : [[default-console-opts]] 

   `:req`            : Incoming HTTP request

   - [Console wiki](https://github.com/nilenso/goose/wiki/Console)"
  [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       wrap-prefix-route
       wrap-method-override
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
