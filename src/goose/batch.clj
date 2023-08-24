(ns goose.batch
  (:require
    [goose.utils :as u]))

(def status-in-progress :in-progress)
(def status-complete :complete)

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
