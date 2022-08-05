(ns goose.brokers.broker
  {:no-doc true})

(defmulti new
          (fn [broker & _] (:type broker)))

(defmethod new :default [x & _]
  (throw (ex-info "Invalid broker type" x)))

(defprotocol Broker
  "Protocol for all Goose brokers."
  (enqueue [this job])
  (schedule [this schedule job])
  (start [this worker-opts])

  ; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this])
  (enqueued-jobs-size [this queue])
  (enqueued-jobs-find-by-id [this queue id])
  (enqueued-jobs-find-by-pattern [this queue match? limit])
  (enqueued-jobs-prioritise-execution [this job])
  (enqueued-jobs-delete [this job])
  (enqueued-jobs-delete-all [this queue])

  ; scheduled-jobs API
  (scheduled-jobs-size [this])
  (scheduled-jobs-find-by-id [this id])
  (scheduled-jobs-find-by-pattern [this match? limit])
  (scheduled-jobs-prioritise-execution [this job])
  (scheduled-jobs-delete [this job])
  (scheduled-jobs-delete-all [this])

  ; dead-jobs API
  (dead-jobs-size [this])
  (dead-jobs-find-by-id [this id])
  (dead-jobs-find-by-pattern [this match? limit])
  (dead-jobs-re-enqueue-for-execution [this job])
  (dead-jobs-delete [this job])
  (dead-jobs-delete-older-than [this epoch-time-ms])
  (dead-jobs-delete-all [this]))
