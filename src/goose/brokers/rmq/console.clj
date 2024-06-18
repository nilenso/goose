(ns ^:no-doc goose.brokers.rmq.console
  (:require [bidi.bidi :as bidi]
            [goose.brokers.rmq.api.dead-jobs :as dead-jobs]
            [goose.brokers.rmq.queue :as rmq-queue]
            [goose.console :as console]
            [goose.defaults :as d]
            [goose.utils :as u]
            [ring.util.response :as response]))

(def header (partial console/header [{:route "/dead" :label "Dead" :job-type :dead}]))

(defn- stats-bar [{:keys [prefix-route] :as page-data}]
  [:main
   [:section.statistics
    (for [{:keys [id label route]} [{:id :dead :label "Dead" :route "/dead"}]]
      [:div.stat {:id id}
       [:span.number (str (get page-data id))]
       [:a {:href (prefix-route route)}
        [:span.label label]]])]])

;; Declare dead-jobs queue to prevent rmq throwing exception
(defn- declare-dead-queue
  [rmq-producer]
  (let [ch (u/random-element (:channels rmq-producer))]
    (rmq-queue/declare ch (merge (:queue-type rmq-producer)
                                 {:queue d/prefixed-dead-queue}))))

(defn homepage [{:keys                     [prefix-route]
                 {:keys [app-name broker]} :console-opts}]
  (let [view (console/layout header stats-bar)
        _ (declare-dead-queue broker)
        data {:dead (dead-jobs/size (u/random-element (:channels broker)))}]
    (response/response (view "Home" (assoc data :app-name app-name
                                                :prefix-route prefix-route)))))

(defn job-page [_]
  [:div.rmq
   [:h1 "Dead Job"]
   [:div.right
    [:input {:type "number" :min "1" :max "10000" :placeholder "No. of jobs"}]
    [:button.btn.btn-lg "Replay"]
    [:button.btn-danger.btn-lg "Pop"]
    [:button.btn-danger.btn-lg "Purge"]]])

(defn get-dead-job [{:keys                     [prefix-route]
                     {:keys [app-name broker]} :console-opts}]
  (let [view (console/layout header job-page)
        _ (declare-dead-queue broker)]
    (response/response (view "Dead" {:job-type     :dead
                                     :app-name     app-name
                                     :prefix-route prefix-route}))))


(defn- routes [route-prefix]
  [route-prefix [["" console/redirect-to-home-page]
                 ["/" homepage]
                 ["/dead" {"" get-dead-job}]
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
