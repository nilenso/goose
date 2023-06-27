(ns goose.broker
  "Defines protocol for Message Broker of Goose.")

(defprotocol Broker
  "Protocol that message brokers should implement
   in order to facilitate transfers between
   producer & consumer, AKA application client & worker.
   - [Guide to Message Broker Integration](https://github.com/nilenso/goose/wiki/Guide-to-Message-Broker-Integration)"
  ;; client/producer
  (enqueue [this job] "Enqueues a Job for async execution.")
  (enqueue-batch [this batch] "Enqueues a Batch of Jobs for async execution.")
  (schedule [this schedule-epoch-ms job] "Schedules a Job for execution at given epoch-ms.")
  (register-cron [this cron-opts job-description] "Registers a function for periodic execution in cron-jobs style.")

  ;; worker/consumer
  (start-worker [this worker-opts] "Starts a worker process that does multiple things:
  - Dequeue & execute jobs from given queue
  - Enqueue scheduled jobs due for execution
  - Enqueue cron jobs due for execution
  - Retry failed jobs & mark them as dead when retries are exhausted
  - Run checks & replay orphan jobs
  - Send metrics around Job execution & state of message broker

  Some tasks are message-broker specific & need not be implemented by all workers.\\
  For instance, RabbitMQ natively supports scheduled jobs & orphan handling,\\
  and it need not be explicitly implemented by it's worker.")

  ;; enqueued-jobs API
  (enqueued-jobs-list-all-queues [this] "Lists all the queues.")
  (enqueued-jobs-size [this queue] "Returns count of Jobs in given queue.")
  (enqueued-jobs-find-by-id [this queue id] "Finds a Job by `:id` in given queue.")
  (enqueued-jobs-find-by-pattern [this queue match? limit] "Finds a Job by user-defined parameters in given queue.")
  (enqueued-jobs-prioritise-execution [this job] "Brings a Job anywhere in the queue to front of queue.")
  (enqueued-jobs-delete [this job] "Deletes given Job from it's queue.")
  (enqueued-jobs-purge [this queue] "Purges all the Jobs in given queue.")

  ;; scheduled-jobs API
  (scheduled-jobs-size [this] "Returns count of Scheduled Jobs.")
  (scheduled-jobs-find-by-id [this id] "Finds a Scheduled Job by `:id`.")
  (scheduled-jobs-find-by-pattern [this match? limit] "Finds a Scheduled Jobs by user-defined parameters.")
  (scheduled-jobs-prioritise-execution [this job] "Enqueues a Job scheduled to run at anytime to front of queue.")
  (scheduled-jobs-delete [this job] "Deletes given Scheduled Job.")
  (scheduled-jobs-purge [this] "Purges all the Scheduled Jobs.")

  ;; cron jobs API
  (cron-jobs-size [this] "Returns count of Periodic Jobs.")
  (cron-jobs-find-by-name [this entry-name] "Finds a Cron Entry by `:name`.")
  (cron-jobs-delete [this entry-name] "Deletes Cron Entry & Cron-Scheduled Job of given `:name`.")
  (cron-jobs-purge [this] "Purges all the Cron Entries & Cron-Scheduled Jobs.")

  ;; dead-jobs API
  (dead-jobs-size [this] "Returns count of Dead Jobs.")
  (dead-jobs-pop [this] "Pops the oldest Dead Job from the queue & returns it.")
  (dead-jobs-find-by-id [this id] "Finds a Dead Job by `:id`.")
  (dead-jobs-find-by-pattern [this match? limit] "Finds a Dead Jobs by user-defined parameters.")
  (dead-jobs-replay-job [this job] "Re-enqueues given Dead Job to front of queue for execution.")
  (dead-jobs-replay-n-jobs [this n] "Re-enqueues n oldest Dead Jobs to front of queue for execution.")
  (dead-jobs-delete [this job] "Deletes given Dead Job.")
  (dead-jobs-delete-older-than [this epoch-ms] "Deletes Dead Jobs older than given epoch-ms.")
  (dead-jobs-purge [this] "Purges all the Dead Jobs."))
