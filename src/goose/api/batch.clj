(ns goose.api.batch
  (:require
    [goose.broker :as b]
    [goose.client]))

(defn- into-batch-map
  [{:keys [batch-state] :as batch}]
  (let [counts (select-keys batch [:enqueued :retrying :successful :dead])
        status (select-keys batch-state [:id :created-at :status :total-jobs])]
    (merge status counts)))

(defn status
  [broker id]
  (let [batch (b/batch-status broker id)]
    (when batch
      (into-batch-map batch))))

(defn delete
  [broker id]
  (b/batch-delete broker id))
