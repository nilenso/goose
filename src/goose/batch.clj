(ns goose.batch
  (:require
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-callback
  "Sample callback for a batch"
  [batch-id
   {:keys [successful dead]}]
  (log/info "Batch:" batch-id "execution completed. Successful:" successful "Dead:" dead))

(def default-opts
  {:linger-sec      3600
   :callback-fn-sym `default-callback})

(def status-in-progress :in-progress)
(def status-success :success)
(def status-dead :dead)
(def status-partial-success :partial-success)
(def terminal-states [status-success status-dead status-partial-success])

(defn terminal-state? [status]
  (some #{status} terminal-states))

(defn status-from-job-states
  [enqueued retrying successful dead]
  (cond
    (< 0 (+ enqueued retrying)) status-in-progress
    (= 0 dead) status-success
    (= 0 successful) status-dead
    :else status-partial-success))

(defn new
  [{:keys [queue ready-queue retry-opts]}
   {:keys [callback-fn-sym linger-sec]}
   jobs]
  (let [id (str (random-uuid))]
    {:id              id
     :callback-fn-sym callback-fn-sym
     :linger-sec      linger-sec
     :queue           queue
     :ready-queue     ready-queue
     :retry-opts      retry-opts
     :jobs            (map #(assoc % :batch-id id) jobs)
     :total           (count jobs)
     :status          status-in-progress
     :created-at      (u/epoch-time-ms)}))

(defn ^:no-doc new-callback
  [{:keys [id callback-fn-sym queue ready-queue retry-opts]} & args]
  (-> (job/new callback-fn-sym args queue ready-queue retry-opts)
      (assoc :callback-for-batch-id id)))
