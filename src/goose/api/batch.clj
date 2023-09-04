(ns goose.api.batch
  (:require
    [goose.broker :as b]
    [goose.client]))

(defn status
  [broker id]
  (when-let [batch (b/batch-status broker id)]
    (select-keys batch [:id :status :enqueued :retrying :success :dead :total :created-at])))

(defn delete
  [broker id]
  (b/batch-delete broker id))
