(ns goose.brokers.redis.console
  (:require [bidi.bidi :as bidi]
            [goose.brokers.redis.console.pages.enqueued :as enqueued]
            [goose.brokers.redis.console.pages.home :as home]
            [ring.util.response :as response]))

(defn- load-css [_]
  (-> "css/style.css"
      response/resource-response
      (response/header "Content-Type" "text/css")))

(defn- load-img [_]
  (-> "img/goose-logo.png"
      response/resource-response
      (response/header "Content-Type" "image/png")))

(defn- load-js [_]
  (-> "js/index.js"
      response/resource-response
      (response/header "Content-Type" "text/javascript")))

(defn- redirect-to-home-page [{:keys [prefix-route]}]
  (response/redirect (prefix-route "/")))

(defn- not-found [_]
  (response/not-found "<div> Not found </div>"))

(defn routes [route-prefix]
  [route-prefix [["" redirect-to-home-page]
                 ["/" home/page]
                 ["/enqueued" {""                 enqueued/page
                               ["/queue/" :queue] [[:get enqueued/page]
                                                   [:delete enqueued/purge-queue]]}]
                 ["/css/style.css" load-css]
                 ["/img/goose-logo.png" load-img]
                 ["/js/index.js" load-js]
                 [true not-found]]])

(defn handler [_ {:keys                                        [uri request-method]
                  {:keys [route-prefix] :or {route-prefix ""}} :console-opts
                  :as                                          req}]
  (let [{page-handler :handler
         route-params :route-params} (-> route-prefix
                                         routes
                                         (bidi/match-route
                                           uri
                                           {:request-method
                                            request-method}))]
    (-> req
        (assoc :route-params route-params)
        page-handler)))
