(ns goose.brokers.broker
  {:no-doc true})

(defprotocol Broker
  "Protocol for all Goose brokers."
  (enqueue [_ job])
  (schedule [_ schedule job])
  (start [_ worker-opts])

  ; enqueued-jobs API
  (enqueued-jobs-list-all-queues [_])
  (enqueued-jobs-size [_ queue])
  (enqueued-jobs-find-by-id [_ queue id])
  (enqueued-jobs-find-by-pattern [_ queue match? limit])
  (enqueued-jobs-prioritise-execution [_ job])
  (enqueued-jobs-delete [_ job])
  (enqueued-jobs-purge [_ queue])

  ; scheduled-jobs API
  (scheduled-jobs-size [_])
  (scheduled-jobs-find-by-id [_ id])
  (scheduled-jobs-find-by-pattern [_ match? limit])
  (scheduled-jobs-prioritise-execution [_ job])
  (scheduled-jobs-delete [_ job])
  (scheduled-jobs-purge [_])

  ; dead-jobs API
  (dead-jobs-size [_])
  (dead-jobs-find-by-id [_ id])
  (dead-jobs-find-by-pattern [_ match? limit])
  (dead-jobs-re-enqueue-for-execution [_ job])
  (dead-jobs-delete [_ job])
  (dead-jobs-delete-older-than [_ epoch-time-ms])
  (dead-jobs-purge [_]))
