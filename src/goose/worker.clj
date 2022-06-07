(ns goose.worker
  (:require
    [goose.defaults :as d]
    [goose.heartbeat :as heartbeat]
    [goose.redis :as r]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.worker :refer [validate-worker-params]]

    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- execute-job
  [redis-conn {:keys [id execute-fn-sym args] :as job}]
  (try
    (apply (u/require-resolve execute-fn-sym) args)
    (catch Exception ex
      (retry/handle-failure redis-conn job ex)))
  (log/debug "Executed job-id:" id))

(def ^:private unblocking-queue-prefix
  "goose/unblocking-queue:")

(defn- extract-job
  [[queue list-member]]
  (when-not (str/starts-with? queue unblocking-queue-prefix)
    list-member))

(defn- pop-job
  [redis-conn prefixed-queue unblocking-queue]
  (let [queues [prefixed-queue unblocking-queue]]
    (extract-job (r/dequeue redis-conn queues))))

(defn- worker
  [{:keys [thread-pool redis-conn
           prefixed-queue unblocking-queue]}]
  (u/while-pool
    thread-pool
    (log/info "Long-Polling Redis...")
    (u/log-on-exceptions
      (when-let [job (pop-job redis-conn prefixed-queue unblocking-queue)]
        (execute-job redis-conn job))))
  (log/info "Stopped worker. Exiting gracefully..."))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [id queue thread-pool internal-thread-pool redis-conn
           unblocking-queue graceful-shutdown-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)
  (cp/shutdown internal-thread-pool)

  ; Interrupt scheduler to exit sleep & terminate thread-pool.
  ; If not interrupted, shutdown time will increase by
  ; max(graceful-shutdown-sec, sleep time)
  (cp/shutdown! internal-thread-pool)

  (heartbeat/stop id redis-conn queue)

  ; Worker threads need not be interrupted.
  ; Unblock redis call by sending dummy value to utility queue
  ; REASON: https://github.com/nilenso/goose/issues/14
  (r/enqueue-back
    redis-conn unblocking-queue "dummy" graceful-shutdown-sec)

  ; Give jobs executing grace time to complete.
  (log/warn "Awaiting executing jobs to complete.")

  (.awaitTermination
    thread-pool
    graceful-shutdown-sec
    TimeUnit/SECONDS)

  ; Set state of thread-pool to STOP.
  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(def default-opts
  {:threads                        1
   :redis-url                      d/default-redis-url
   :redis-pool-opts                {}
   :queue                          d/default-queue
   :scheduler-polling-interval-sec 5
   :graceful-shutdown-sec          30})

(defn start
  "Starts a threadpool for worker."
  [{:keys [threads redis-url redis-pool-opts
           queue scheduler-polling-interval-sec
           graceful-shutdown-sec]}]
  (validate-worker-params
    redis-url redis-pool-opts queue
    scheduler-polling-interval-sec
    graceful-shutdown-sec threads)
  (let [thread-pool (cp/threadpool threads)
        internal-thread-pool (cp/threadpool 2)
        random-str (subs (str (random-uuid)) 24 36) ; Take last 12 chars of UUID.
        opts {:id                             (str queue ":" (u/host-name) ":" random-str)
              :thread-pool                    thread-pool
              :internal-thread-pool           internal-thread-pool
              :redis-conn                     (r/conn redis-url redis-pool-opts)

              :queue                          queue
              :prefixed-queue                 (str d/queue-prefix queue)
              ; REASON: https://github.com/nilenso/goose/issues/14
              :unblocking-queue               (str unblocking-queue-prefix random-str)

              :graceful-shutdown-sec          graceful-shutdown-sec
              :scheduler-polling-interval-sec scheduler-polling-interval-sec}]
    (heartbeat/start opts)
    (cp/future internal-thread-pool (heartbeat/recur opts))

    (cp/future internal-thread-pool (scheduler/run opts))

    (dotimes [_ threads]
      (cp/future thread-pool (worker opts)))

    (reify Shutdown
      (stop [_] (internal-stop opts)))))
