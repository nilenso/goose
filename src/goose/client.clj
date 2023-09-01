(ns goose.client
  "Functions for executing job in async, scheduled or periodic manner."
  (:require
    [goose.batch :as batch]
    [goose.broker :as b]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.retry :as retry]
    [goose.utils :as u])
  (:import
    (java.time Instant)))

(def default-opts
  "Map of sample configs for producing jobs.

  ### Keys
  `:broker`     : Message broker that transfers message from Producer to Consumer.\\
  Given value must implement [[goose.broker/Broker]] protocol.\\
  [Message Broker wiki](https://github.com/nilenso/goose/wiki/Message-Brokers)

  `:queue`      : Destination where client produces to & worker consumes from.\\
  Example       : [[d/default-queue]]

  `:retry-opts` : Configuration for handling Job failure.\\
  Example       : [[retry/default-opts]]\\
  [Error Handling & Retries wiki](https://github.com/nilenso/goose/wiki/Error-Handling-&-Retries)"
  {:queue      d/default-queue
   :retry-opts retry/default-opts})

(defn- prefix-queues-inside-opts
  [{:keys [queue retry-opts] :as opts}]
  (assoc opts
    :ready-queue (d/prefix-queue queue)
    :retry-opts (retry/prefix-queue-if-present retry-opts)))

(defn- register-cron-schedule
  [{:keys [broker queue ready-queue retry-opts] :as _opts}
   cron-opts
   execute-fn-sym
   args]
  (let [job-description (j/description execute-fn-sym args queue ready-queue retry-opts)
        cron-entry (b/register-cron broker cron-opts job-description)]
    (select-keys cron-entry [:cron-name :cron-schedule :timezone])))

(defn- create-job
  [{:keys [queue retry-opts ready-queue]} execute-fn-sym args]
  (j/new execute-fn-sym args queue ready-queue retry-opts))

(defn- enqueue
  [{:keys [broker] :as opts}
   schedule-epoch-ms
   execute-fn-sym
   args]
  (let [internal-opts (prefix-queues-inside-opts opts)
        job (create-job internal-opts execute-fn-sym args)]
    (if schedule-epoch-ms
      (b/schedule broker schedule-epoch-ms job)
      (b/enqueue broker job))))

(defn perform-async
  "Enqueues a function for async execution.

  ### Args
  `client-opts`    : Map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Variadic values provided in given order when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.

  ### Usage
  ```Clojure
  (perform-async client-opts `send-emails \"subject\" \"body\" [:user-1 :user-2])
  ```

  - [Getting Started wiki](https://github.com/nilenso/goose/wiki/Getting-Started)."
  [opts execute-fn-sym & args]
  (enqueue opts nil execute-fn-sym args))

(defn perform-at
  "Schedules a function for execution at given date & time.

  ### Args
  `client-opts`      : Map of `:broker`, `:queue` & `:retry-opts`.\\
  Example            : [[default-opts]]

  `^Instant instant` : `java.time.Instant` at which job should be executed.

  `execute-fn-sym`   : A fully-qualified function symbol called by worker.\\
  Example            : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`             : Variadic values provided in given order when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.

   ### Usage
   ```Clojure
   (let [instant (java.time.Instant/parse \"2022-10-31T18:46:09.00Z\")]
     (perform-at client-opts instant `send-emails \"subject\" \"body\" [:user-1 :user-2]))
   ```

   - [Scheduled Jobs wiki](https://github.com/nilenso/goose/wiki/Scheduled-Jobs)"
  [opts ^Instant instant execute-fn-sym & args]
  (enqueue opts (u/epoch-time-ms instant) execute-fn-sym args))

(defn perform-in-sec
  "Schedules a function for execution with a delay of given seconds.

  ### Args
  `client-opts`    : Map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `sec`            : Delay of Job execution in seconds.

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Variadic values provided in given order when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.

   ### Usage
   ```Clojure
   (perform-in-sec default-opts 300 `send-emails \"subject\" \"body\" [:user-1 :user-2])
   ```

  - [Scheduled Jobs wiki](https://github.com/nilenso/goose/wiki/Scheduled-Jobs)"
  [opts sec execute-fn-sym & args]
  (enqueue opts (u/sec+current-epoch-ms sec) execute-fn-sym args))

(defn perform-every
  "Registers a function for periodic execution in cron-jobs style.\\
  `perform-every` is idempotent.\\
  If a cron entry already exists with the same name, it will be overwritten with new data.

  ### Args
  `client-opts`    : Map of `:broker`, `:queue` & `:retry-opts`.\\
  Example          : [[default-opts]]

  `cron-opts`      : Map of `:cron-name`, `:cron-schedule`, `:timezone`
  - `:cron-name` (Mandatory)
    - Unique identifier of a periodic job
  - `:cron-schedule` (Mandatory)
    - Unix-style schedule
  - `:timezone` (Optional)
    - Timezone for executing the Job at schedule
    - Acceptable timezones: `(java.time.ZoneId/getAvailableZoneIds)`
    - Defaults to system timezone

  `execute-fn-sym` : A fully-qualified function symbol called by worker.\\
  Example          : ```my-fn`, ```ns-alias/my-fn`, `'fully-qualified-ns/my-fn`

  `args`           : Variadic values provided in given order when invoking `execute-fn-sym`.\\
   Given values must be serializable by `ptaoussanis/nippy`.

  ### Usage
  ```Clojure
  (let [cron-opts {:cron-name     \"my-periodic-job\"
                   :cron-schedule \"0 10 15 * *\"
                   :timezone      \"US/Pacific\"}]
    (perform-every client-opts cron-opts `send-emails \"subject\" \"body\" [:user-1 :user-2]))
  ```

  - [Periodic Jobs wiki](https://github.com/nilenso/goose/wiki/Periodic-Jobs)"
  [opts cron-opts execute-fn-sym & args]
  (let [internal-opts (prefix-queues-inside-opts opts)]
    (register-cron-schedule internal-opts cron-opts execute-fn-sym args)))

(defn perform-batch
  "Enqueues a batch of jobs for async execution."
  ([opts batch-opts execute-fn-sym args-coll]
   (let [internal-opts (prefix-queues-inside-opts opts)
         jobs (map #(create-job internal-opts execute-fn-sym %) args-coll)
         batch (batch/new internal-opts batch-opts jobs)]
     (b/enqueue-batch (:broker internal-opts) batch))))
