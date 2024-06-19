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

(defn job-page [{:keys [base-path total-jobs job replay-job-count] :as data}]
  [:div.rmq
   [:div.top
    [:h1 "Dead Job"]
    (when (and total-jobs (> total-jobs 0))
      [:div.right
       [:div.top
        [:form.replay-jobs {:action (str base-path "/jobs")
                            :method "post"}
         [:input.input {:type "number" :min "1" :max "10000" :placeholder "No. of jobs" :name "replay"}]
         [:input.btn.btn-lg.replay
          {:type "submit" :value "Replay"}]]

        [:form {:action (str base-path "/job")
                :method "post"}
         (console/delete-confirm-dialog "Are you sure you want to pop queue?")
         [:input.btn.btn-danger.btn-lg
          {:type "button" :value "Pop" :class "delete-dialog-show"}]]

        [:div.bottom
         (console/purge-confirmation-dialog data)
         [:button {:class "btn btn-danger btn-lg purge-dialog-show"} "Purge"]]]])]
   (when (= total-jobs 0)
     (console/flash-msg {:type    :error
                         :message "No jobs found"}))
   (when (and replay-job-count (> replay-job-count 0))
     (console/flash-msg {:type    :success
                         :message (str replay-job-count " job/s replayed")}))
   (when job
     [:div [:h2 "Popped Job"]
      (console/job-table job)])])

(defn get-dead-job [{:keys                     [prefix-route]
                     {:keys [app-name broker]} :console-opts}]
  (let [view (console/layout header job-page)
        _ (declare-dead-queue broker)
        total-jobs (dead-jobs/size (u/random-element (:channels broker)))]
    (response/response (view "Dead" {:total-jobs   total-jobs
                                     :job-type     :dead
                                     :base-path    (prefix-route "/dead")
                                     :app-name     app-name
                                     :prefix-route prefix-route}))))

(defn purge-dead-queue [{{:keys [broker]} :console-opts
                         :keys            [prefix-route]}]
  (dead-jobs/purge (u/random-element (:channels broker)))
  (response/redirect (prefix-route "/dead")))

(defn replay-jobs [{{:keys [app-name broker]} :console-opts
                    :keys                     [prefix-route]
                    {:keys [replay]}          :params}]
  (let [view (console/layout header job-page)
        replay-job-count-in-req (Integer/parseInt replay)
        replay-job-count (when (> replay-job-count-in-req 0) (dead-jobs/replay-n-jobs (u/random-element (:channels broker))
                                                                                      (:queue-type broker)
                                                                                      (:publisher-confirms broker)
                                                                                      replay-job-count-in-req))
        total-jobs (dead-jobs/size (u/random-element (:channels broker)))]
    (response/response (view "Dead" {:total-jobs       total-jobs
                                     :replay-job-count replay-job-count
                                     :job-type         :dead
                                     :base-path        (prefix-route "/dead")
                                     :app-name         app-name
                                     :prefix-route     prefix-route}))))

(defn pop-dead-queue [{{:keys [app-name broker]} :console-opts
                       :keys                     [prefix-route]}]
  (let [view (console/layout header job-page)
        total-jobs (dead-jobs/size (u/random-element (:channels broker)))
        response (if (> total-jobs 0)
                   {:total-jobs (dec total-jobs)
                    :job        (dead-jobs/pop (u/random-element (:channels broker)))}
                   {:total-jobs 0})]
    (response/response (view "Dead" (assoc response :job-type :dead
                                                    :base-path (prefix-route "/dead")
                                                    :app-name app-name
                                                    :prefix-route prefix-route)))))

(defn- routes [route-prefix]
  [route-prefix [["" console/redirect-to-home-page]
                 ["/" homepage]
                 ["/dead" {""      [[:get get-dead-job]
                                    [:delete purge-dead-queue]]
                           "/job"  [[:delete pop-dead-queue]]
                           "/jobs" [[:post replay-jobs]]}]
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
