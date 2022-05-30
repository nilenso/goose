(ns goose.worker
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]
    [goose.validations.worker :refer [validate-worker-params]]

    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- namespace-sym [fn-sym]
  (-> fn-sym
      (str)
      (str/split #"/")
      (first)
      (symbol)))

(defn- execute-job
  [{:keys [id fn-sym args]}]
  (require (namespace-sym fn-sym))
  (apply (resolve fn-sym) args)
  (log/debug "Executed job-id:" id))

(def ^:private unblocking-queue-prefix
  "goose/unblocking-queue:")

(defn- generate-unblocking-queue []
  (str unblocking-queue-prefix (random-uuid)))

(defn- extract-job
  [[queue list-member]]
  (when-not (str/starts-with? queue unblocking-queue-prefix)
    list-member))

(defn- pop-job
  [redis-conn prefixed-queue unblocking-queue]
  (let [queues [prefixed-queue unblocking-queue]]
    (extract-job (r/dequeue redis-conn queues))))

(defmacro while-pool
  [pool & body]
  `(while (not (cp/shutdown? ~pool))
     ~@body))

(defn- worker
  [{:keys [thread-pool redis-conn
           prefixed-queue unblocking-queue]}]
  (while-pool
    thread-pool
    (log/info "Long-Polling Redis...")
    (u/log-on-exceptions
      (when-let [job (pop-job redis-conn prefixed-queue unblocking-queue)]
        (execute-job job))))
  (log/info "Stopped worker. Exiting gracefully..."))

(defn- scheduler
  [{:keys [thread-pool redis-conn
           scheduled-jobs-polling-interval-sec]}]
  (while-pool thread-pool
    (log/info "Polling Scheduled Jobs...")
    (u/log-on-exceptions
      (let [schedule-queue (str d/queue-prefix d/schedule-queue)
            jobs (r/scheduled-jobs-due-now redis-conn schedule-queue)
            grouped-jobs (group-by :queue jobs)]
        (if (empty? jobs)
          (Thread/sleep (* 1000 scheduled-jobs-polling-interval-sec))
          (r/enqueue-due-jobs-to-front redis-conn schedule-queue grouped-jobs)))))
  (log/info "Stopped scheduler. Exiting gracefully..."))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [thread-pool redis-conn
           unblocking-queue graceful-shutdown-time-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)

  ; REASON: https://github.com/nilenso/goose/issues/14
  (r/enqueue-with-expiry
    redis-conn unblocking-queue "dummy" graceful-shutdown-time-sec)

  (log/warn "Awaiting all threads to terminate.")
  (.awaitTermination
    thread-pool
    graceful-shutdown-time-sec
    TimeUnit/SECONDS)

  ; Set state of thread-pool to STOP.
  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(def default-opts
  {:threads                             1
   :redis-url                           d/default-redis-url
   :redis-pool-opts                     {}
   :queue                               d/default-queue
   :scheduled-jobs-polling-interval-sec 5
   :graceful-shutdown-time-sec          30})

(defn start
  "Starts a threadpool for worker."
  [{:keys [threads redis-url redis-pool-opts
           queue scheduled-jobs-polling-interval-sec
           graceful-shutdown-time-sec]}]
  (validate-worker-params
    redis-url redis-pool-opts queue
    scheduled-jobs-polling-interval-sec
    graceful-shutdown-time-sec threads)
  (let [thread-pool (cp/threadpool (+ 1 threads)) ; An extra thread to poll for scheduled jobs.
        opts {:thread-pool                         thread-pool
              :redis-conn                          (r/conn redis-url redis-pool-opts)
              :prefixed-queue                      (str d/queue-prefix queue)
              ; Reason for having a utility unblocking queue:
              ; https://github.com/nilenso/goose/issues/14
              :unblocking-queue                    (generate-unblocking-queue)
              :graceful-shutdown-time-sec          graceful-shutdown-time-sec
              :scheduled-jobs-polling-interval-sec scheduled-jobs-polling-interval-sec}]
    (dotimes [_s threads]
      (cp/future thread-pool (worker opts)))
    (cp/future thread-pool (scheduler opts))
    (reify Shutdown
      (stop [_] (internal-stop opts)))))
