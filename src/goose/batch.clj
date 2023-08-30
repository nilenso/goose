(ns goose.batch
  (:require
    [goose.utils :as u]
    [goose.defaults :as d]

    [clojure.tools.logging :as log]))

(def status-in-progress :in-progress)
(def status-complete :complete)

(defn default-callback
  "Sample callback for a batch"
  [batch-id
   {:keys [successful dead]}]
  (log/info "Batch:" batch-id " execution completed with successful:" successful ", dead:" dead))

(def default-opts
  {:linger-sec      d/redis-batch-linger-sec
   :callback-fn-sym `default-callback})

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
     :created-at      (u/epoch-time-ms)}))

(defn status-from-counts
  [{:keys [enqueued retrying]}]
  (cond
    (= 0 (+ enqueued retrying)) status-complete
    :else status-in-progress))
