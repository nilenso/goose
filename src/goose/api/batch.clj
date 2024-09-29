(ns goose.api.batch
  (:require
   [goose.broker :as b]
   [goose.client]))

(defn status
  "For given `:batch-id`, reports progress of a batch.

  `batch/status API` will return nil if a batch has been
  cleaned-up from message broker post completion.
  Increase `:linger-sec` to retain metadata for a longer period."
  [broker batch-id]
  (when-let [batch (b/batch-status broker batch-id)]
    (select-keys batch [:id :status :enqueued :retrying :success :dead :total :created-at])))

(defn delete
  "For given `:batch-id`, deletes Batch metadata and associated enqueued/retrying jobs.

  ### Redis
  Deleting jobs associated to a batch is an expensive operation.\\
  Use `batch/delete API` sparingly for Redis message broker."
  [broker batch-id]
  (b/batch-delete broker batch-id))
