(ns goose.api.batch
  (:require
    [goose.broker :as b]
    [goose.client]))

(defn status
  [broker id]
  (let [batch (b/batch-status broker id)]
    (when batch
      (select-keys batch [:id :status :enqueued :retrying :successful :dead :total :created-at]))))

(defn delete
  [broker id]
  (b/batch-delete broker id))
