(ns goose.broker
  "Defines protocol a message broker of Goose must implement.")

(defprotocol Broker
  "Protocol that message brokers should implement
  in order to facilitate transfers between
  producer & consumer AKA application client & worker."
  ;; client/producer
  (enqueue [this job] "Enqueues a Job for async execution.")
  (schedule [this schedule job] "Schedules a Job for execution at given epoch-ms.")
  (register-cron [this cron-opts job-description] "Registers a function for periodic execution in cron-jobs style.")

  ;; worker/consumer
  (start-worker [this worker-opts] "Start a worker process that does multiple things:
  - Dequeue & execute jobs from given queue
  - Enqueue scheduled jobs which are due for execution
  - Enqueue cron jobs which are due for execution
  - Retry failed jobs & mark them as dead when retries are exhausted
  - Run checks & replay orphan jobs
  - Send metrics around Job execution & state of message broker")

  ;; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this] "Lists all the queues.")
  (enqueued-jobs-size [this queue] "Returns number of jobs in given queue.")
  (enqueued-jobs-find-by-id [this queue id] "Finds a Job by `:id` in given queue.")
  (enqueued-jobs-find-by-pattern [this queue match? limit] "Finds a Job by user-defined parameters in given queue.")
  (enqueued-jobs-prioritise-execution [this job] "Brings a Job anywhere in the queue to front of queue.")
  (enqueued-jobs-delete [this job] "Deletes a Job in given queue.")
  (enqueued-jobs-purge [this queue] "Purges all Jobs in given queue.")

  ;; scheduled-jobs API
  (scheduled-jobs-size [this] "Returns number of Scheduled Jobs.")
  (scheduled-jobs-find-by-id [this id] "Finds a Scheduled Job by `:id`.")
  (scheduled-jobs-find-by-pattern [this match? limit] "Finds a Scheduled Jobs by user-defined parameters.")
  (scheduled-jobs-prioritise-execution [this job] "Enqueues a Job scheduled to run at anytime to front of queue.")
  (scheduled-jobs-delete [this job] "Deletes a Scheduled Job.")
  (scheduled-jobs-purge [this] "Purges all the Scheduled jobs.")

  ;; dead-jobs API
  (dead-jobs-size [this] "Returns number of Dead Jobs.")
  (dead-jobs-pop [this] "Pops the oldest Dead Job from the queue & returns it.")
  (dead-jobs-find-by-id [this id] "Finds a Dead Job by `:id`.")
  (dead-jobs-find-by-pattern [this match? limit] "Finds a Dead Jobs by user-defined parameters.")
  (dead-jobs-replay-job [this job] "Re-enqueues given Dead Job for execution.")
  (dead-jobs-replay-n-jobs [this n] "Re-enqueues n Dead Jobs for execution.")
  (dead-jobs-delete [this job] "Deletes a Dead Job.")
  (dead-jobs-delete-older-than [this epoch-time-ms] "Delete Dead jobs older than given epoch-ms.")
  (dead-jobs-purge [this] "Purges all the Dead jobs.")

  ;; cron jobs API
  (cron-jobs-size [this] "Returns number of Periodic Jobs.")
  (cron-jobs-find-by-name [this entry-name] "Finds a Cron Job by `:name`.")
  (cron-jobs-delete [this entry-name] "Deletes Cron Job of given `:name`.")
  (cron-jobs-delete-all [this] "Purges all the Cron Jobs."))
