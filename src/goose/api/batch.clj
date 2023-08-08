(ns goose.api.batch
  (:require [goose.batch :as batch]
            [goose.broker :as b]
            [goose.client]))

(defn- into-batch-map
  [batch]
  (let [counts (select-keys batch [:enqueued :retrying :successful :dead])
        total (->> (vals counts)
                (reduce +))
        status {:id         (get-in batch [:batch-state :id])
                :created-at (get-in batch [:batch-state :created-at])
                :status     (batch/status-from-counts counts total)
                :total      total}]
    (merge status counts)))

(defn status
  [broker id]
  (let [batch (b/batch-status broker id)]
    (when batch
      (into-batch-map batch))))
