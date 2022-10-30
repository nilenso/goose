(ns goose.client
  "Functions for executing job in async, scheduled or periodic manner."
  (:require
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.retry :as retry]
    [goose.utils :as u])
  (:import
    (java.time Instant)))

(def default-opts
  "Map of sample configs for enqueuing jobs.

  Keys:

  `:broker`     : Message broker that transfers message from Producer to Consumer.\\
  Given value must implement [[goose.broker/Broker]] protocol.\\
  [Message Broker wiki](https://github.com/nilenso/goose/wiki/Message-Brokers)

  `:queue`      : Destination where client produces to & worker consumes from.\\
  Example       : [[goose.defaults/default-queue]]

  `:retry-opts` : Configuration for handling Job failure.\\
  Example       : [[goose.retry/default-opts]]\\
  [Error Handling & Retries wiki](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries)"
  {:queue      d/default-queue
   :retry-opts retry/default-opts})

(defn- register-cron-schedule
  [{:keys [broker queue retry-opts] :as _opts}
   cron-opts
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        ready-queue (d/prefix-queue queue)
        job-description (j/description execute-fn-sym args queue ready-queue retry-opts)
        cron-entry (b/register-cron broker cron-opts job-description)]
    (select-keys cron-entry [:cron-name :cron-schedule :timezone])))

(defn- enqueue
  [{:keys [broker queue retry-opts]}
   schedule
   execute-fn-sym
   args]
  (let [retry-opts (retry/prefix-queue-if-present retry-opts)
        ready-queue (d/prefix-queue queue)
        job (j/new execute-fn-sym args queue ready-queue retry-opts)]

    (if schedule
      (b/schedule broker schedule job)
      (b/enqueue broker job))))

(defn ^{:added "0.3.0"} perform-async
  "Enqueues a function for async execution.

  Args:

  `client-opts`    : A map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Values provided when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.\\
   `args` being variadic, it can be as long as number parameters required by `execute-fn-sym`.

  [Getting Started wiki](https://github.com/nilenso/goose/wiki/Getting-Started)."
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn ^{:added "0.3.0"} perform-at
  "Schedules a function for execution at given date & time.

  Args:

  `client-opts`      : A map of `:broker`, `:queue` & `:retry-opts`.\\
  Example            : [[default-opts]]

  `^Instant instant` : `java.time.Instant` at which job should be executed.

  `execute-fn-sym`   : A fully-qualified function symbol called by worker.\\
  Example            : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`             : Values provided when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.\\
   `args` being variadic, it can be as long as number parameters required by `execute-fn-sym`.

  [Scheduled Jobs wiki](https://github.com/nilenso/goose/wiki/Scheduled-Jobs)"
  [opts ^Instant instant execute-fn-sym & args]
  (enqueue opts (u/epoch-time-ms instant) execute-fn-sym args))

(defn ^{:added "0.3.0"} perform-in-sec
  "Schedules a function for execution with a delay of given seconds.

  Args:

  `client-opts`    : A map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `sec`            : Delay of Job execution in seconds.

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Values provided when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.\\
   `args` being variadic, it can be as long as number parameters required by `execute-fn-sym`.

  [Scheduled Jobs wiki](https://github.com/nilenso/goose/wiki/Scheduled-Jobs)"
  [opts sec execute-fn-sym & args]
  (enqueue opts (u/add-sec sec) execute-fn-sym args))

(defn ^{:added "0.3.0"} perform-every
  "Registers a function for periodic execution in cron-jobs style.\\
  `perform-every` is idempotent.\\
  If a cron entry already exists with the same name, it will be overwritten with new data.

  Args:

  `client-opts`    : A map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `cron-opts`      : A map of `:cron-name`, `:cron-schedule`, `:timezone`
  - `:cron-name` (Mandatory)
    - Unique identifier of a periodic job
    - Example: `\"my-periodic-job\"`
  - `:cron-schedule` (Mandatory)
    - Unix-style schedule
    - Example: `\"0 10 15 * *\"`
  - `:timezone` (Optional)
    - Timezone for executing the Job at schedule
    - Acceptable timezones: `(java.time.ZoneId/getAvailableZoneIds)`
    - Defaults to system timezone
    - Example: `\"US/Pacific\"`

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Values provided when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.\\
   `args` being variadic, it can be as long as number parameters required by `execute-fn-sym`.

  [Periodic Jobs wiki](https://github.com/nilenso/goose/wiki/Periodic-Jobs)"
  [opts cron-opts execute-fn-sym & args]
  (register-cron-schedule opts cron-opts execute-fn-sym args))
