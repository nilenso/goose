(ns ^:no-doc goose.brokers.redis.console
  (:require [bidi.bidi :as bidi]
            [goose.brokers.redis.console.pages.dead :as dead]
            [goose.brokers.redis.console.pages.enqueued :as enqueued]
            [goose.brokers.redis.console.pages.home :as home]
            [goose.brokers.redis.console.pages.scheduled :as scheduled]
            [goose.console :as console]))

(defn routes [route-prefix]
  [route-prefix [["" console/redirect-to-home-page]
                 ["/" home/page]
                 ["/enqueued" {""                 enqueued/get-jobs
                               ["/queue/" :queue] [[:get enqueued/get-jobs]
                                                   [:delete enqueued/purge-queue]
                                                   ["/jobs" [[:delete enqueued/delete-jobs]
                                                             [:post enqueued/prioritise-jobs]]]
                                                   [["/job/" :id] [[:get enqueued/get-job]
                                                                   [:post enqueued/prioritise-job]
                                                                   [:delete enqueued/delete-job]]]]}]
                 ["/dead" {""            [[:get dead/get-jobs]
                                          [:delete dead/purge-queue]]
                           "/jobs"       [[:post dead/replay-jobs]
                                          [:delete dead/delete-jobs]]
                           ["/job/" :id] [[:get dead/get-job]
                                          [:post dead/replay-job]
                                          [:delete dead/delete-job]]}]
                 ["/scheduled" {""           [[:get scheduled/get-jobs]
                                              [:delete scheduled/purge-queue]]
                                "/jobs"      [[:post scheduled/prioritise-jobs]
                                              [:delete scheduled/delete-jobs]]
                                ["/job/" :id] [[:get scheduled/get-job]
                                              [:post scheduled/prioritise-job]
                                              [:delete scheduled/delete-job]]}]
                 ["/css/style.css" console/load-css]
                 ["/img/goose-logo.png" console/load-img]
                 ["/js/index.js" console/load-js]
                 [true console/not-found]]])

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
        (update :params merge route-params)
        page-handler)))
