(ns goose.console
  (:require [clojure.string :as str]
            [goose.broker :as b]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params]
            [ring.util.response :as response]))

;; Page handlers
(defn load-css
  "Load static css file"
  [_]
  (-> "css/style.css"
      response/resource-response
      (response/header "Content-Type" "text/css")))

(defn load-img
  "Load goose logo"
  [_]
  (-> "img/goose-logo.png"
      response/resource-response
      (response/header "Content-Type" "image/png")))

(defn load-js
  "Load javascript file"
  [_]
  (-> "js/index.js"
      response/resource-response
      (response/header "Content-Type" "text/javascript")))

(defn redirect-to-home-page
  "Redirects the user to the home page using given prefix-route function"
  [{:keys [prefix-route]}]
  (response/redirect (prefix-route "/")))

(defn not-found
  "A default not found response"
  [_]
  (response/not-found "<div> Not found </div>"))

;; View components
(defn layout
  "Generates HTML layout for webpage using provided hiccup components

  ### Args

  components: A variadic list of functions. Each function is expected to accept
              a map `data` and return hiccup data representing part of a webpage

  ### Usage
   ```Clojure
   (def job [job]
        [:p {:class \"job\"} (:id job)])

       (let [view (layout job)]
          (view \"Jobs page\" {:id \"jid\"})
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

(defn header
  "Creates a navbar header in hiccup syntax with goose logo and app name

  ### Args
  `header-items` : A seq of maps each containing route and label keys to
  specify navigation links

  `data` : A map that should include:
      - `uri`: The current page's URI to identify the active link
      - `app-name`: Application's name
      - `prefix-route`: Function to prepend paths for URL generation"
  [header-items {:keys [app-name prefix-route uri] :or {app-name ""}}]
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

(defn wrap-prefix-route
  "Middleware that injects a `prefix-route` function into the request,
  that facilitates URL construction in views by prepending given route-prefix to paths"
  [handler]
  (fn [{{:keys [route-prefix]} :console-opts
        :as                    req}]
    (let [prefix-route-fn (partial str route-prefix)]
      (handler (assoc req :prefix-route prefix-route-fn)))))

(defn wrap-method-override
  "Middleware to override HTTP method based on the `_method` parameters in request params, allowing
  to simulate PUT, DELETE, PATCH etc. methods that are not supported in HTML forms"
  [handler]
  (fn [request]
    (if-let [overridden-method (get-in request [:params :_method])]
      (handler (assoc request :request-method (keyword overridden-method)))
      (handler request))))

(defn app-handler
  "A Ring handler that sets up the necessary middleware and serves Goose Console UI
  It takes two arguments:

  ### Keys
  `:console-opts`    : A map containing the configuration options for the console \\
     `:route-prefix` : The route path that exposes the Goose Console via app-handler (should not include a trailing \"/\") \\
     `:broker`       : Message broker that transfers message from Producer to Consumer.
                       Given value must implement [[goose.broker/Broker]] protocol.
                       [Message Broker wiki](https://github.com/nilenso/goose/wiki/Message-Brokers)
     `:app-name`     : Name of the application using Goose

   `:req`               : The Ring request map representing the incoming HTTP request."
  [{:keys [broker] :as console-opts} req]
  ((-> (partial b/handler broker)
       wrap-prefix-route
       wrap-method-override
       ring-keyword-params/wrap-keyword-params
       ring-params/wrap-params) (assoc req :console-opts console-opts)))
