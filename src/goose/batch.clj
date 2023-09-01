(ns goose.batch
  (:require
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(defn default-callback
  "Sample callback for a batch"
  [batch-id
   {:keys [successful dead]}]
  (log/info "Batch:" batch-id "execution completed. Successful:" successful "Dead:" dead))

(def default-opts
  {:linger-sec      d/redis-batch-linger-sec
   :callback-fn-sym `default-callback})

(def in-progress :in-progress)
(def success :success)
(def dead :dead)
(def partial-success :partial-success)
(def terminal-states [success dead partial-success])

(defn terminal-state? [status]
  (some #{status} terminal-states))

(defn status-from-job-states
  [successful-jobs dead-jobs total-jobs]
  (cond
    (= total-jobs successful-jobs) success
    (= total-jobs dead-jobs) dead
    (= total-jobs (+ successful-jobs dead-jobs)) partial-success
    :else in-progress))

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
     :total-jobs      (count jobs)
     :status          in-progress
     :created-at      (u/epoch-time-ms)}))

(defn ^:no-doc new-callback
  [{:keys [id callback-fn-sym queue ready-queue retry-opts]}
   callback-args]
  (-> (job/new callback-fn-sym callback-args queue ready-queue retry-opts)
      (assoc :callback-for-batch-id id)))
