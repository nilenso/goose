(ns goose.broker)

(defprotocol Broker
  "Protocol for all Goose brokers."
  (enqueue [this job])
  (schedule [this schedule job])
  (register-cron [this cron-opts job-description])
  (start-worker [this worker-opts])

  ;; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this])
  (enqueued-jobs-size [this queue])
  (enqueued-jobs-find-by-id [this queue id])
  (enqueued-jobs-find-by-pattern [this queue match? limit])
  (enqueued-jobs-prioritise-execution [this job])
  (enqueued-jobs-delete [this job])
  (enqueued-jobs-purge [this queue])

  ;; scheduled-jobs API
  (scheduled-jobs-size [this])
  (scheduled-jobs-find-by-id [this id])
  (scheduled-jobs-find-by-pattern [this match? limit])
  (scheduled-jobs-prioritise-execution [this job])
  (scheduled-jobs-delete [this job])
  (scheduled-jobs-purge [this])

  ;; dead-jobs API
  (dead-jobs-size [this])
  (dead-jobs-pop [this])
  (dead-jobs-find-by-id [this id])
  (dead-jobs-find-by-pattern [this match? limit])
  (dead-jobs-replay-job [this job])
  (dead-jobs-replay-n-jobs [this n])
  (dead-jobs-delete [this job])
  (dead-jobs-delete-older-than [this epoch-time-ms])
  (dead-jobs-purge [this])

  ;; cron jobs API
  (cron-jobs-size [this])
  (cron-jobs-find-by-name [this entry-name])
  (cron-jobs-delete [this entry-name])
  (cron-jobs-delete-all [this]))
