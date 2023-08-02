(ns goose.batch
  (:require
    [goose.utils :as u]))

(def status-in-progress "in-progress")
(def status-complete "complete")

(defn new
  [{:keys [callback-fn-sym]} jobs]
  (let [id (str (random-uuid))]
    {:id              id
     :callback-fn-sym callback-fn-sym
     :jobs            (map #(assoc % :batch-id id) jobs)
     :created-at      (u/epoch-time-ms)}))

(defn status-from-counts
  [{:keys [successful dead]}
   total]
  (cond
    (= (+ successful dead) total) status-complete
    :else status-in-progress))

