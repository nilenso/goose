(ns goose.console
  "Functions to serve the console web interface"
  (:require [clojure.string :as str]
            [goose.broker :as b]
            [goose.defaults :as d]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]
            [ring.util.response :as response]))

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


(defn ^:no-doc header
  "Creates a navbar header in hiccup syntax consisting of goose logo and app name

   ### Args

  `header-items` : A seq of maps each containing route and label keys to
   specify navigation links \\

   `data` : A map that includes: \\

   - `uri`         : The current page's URI to check to highlight as active link \\
   - `app-name`    : Application's name \\
   - `prefix-route`: Function to prepend paths to generate url \\"

  [header-items {:keys [app-name prefix-route uri] :or {app-name ""}
                 :as   _data}]
  (let [subroute? (fn [r] (str/includes? uri (prefix-route r)))
        short-app-name (if (> (count app-name) 20)
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
        (for [{:keys [route label]} header-items]
          [:a {:href  (prefix-route route)
               :class (when (subroute? route) "highlight")} label])]]]]))

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
    *Example*     : [[goose.defaults/route-prefix]] \\

  `:app-name`     : Name to be displayed in console navbar \\
    *Example*     : [[goose.defaults/app-name]] \\"
  {:route-prefix d/route-prefix
   :app-name     d/app-name})

(defn app-handler
  "A Ring handler that sets up the necessary middleware and serves Goose Console. \\

   It takes two arguments: \\

   ### Args

   `:console-opts`   : A map of `:broker`, `:route-prefix`, & `app-name` \\
    *Example*        : [[default-console-opts]] \\

   `:req`            : Incoming HTTP request \\

   - [Console wiki](https://github.com/nilenso/goose/wiki/Console)"
  [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       wrap-prefix-route
       wrap-method-override
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
