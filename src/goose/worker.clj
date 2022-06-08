(ns goose.worker
  (:require
    [goose.defaults :as d]
    [goose.executor :as executor]
    [goose.heartbeat :as heartbeat]
    [goose.orphan-checker :as orphan-checker]
    [goose.redis :as r]
    [goose.scheduler :as scheduler]
    [goose.utils :as u]
    [goose.validations.worker :refer [validate-worker-params]]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp])
  (:import
    [java.util.concurrent TimeUnit]))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [id queue thread-pool internal-thread-pool
           redis-conn graceful-shutdown-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)
  (cp/shutdown internal-thread-pool)

  ; Interrupt scheduler to exit sleep & terminate thread-pool.
  ; If not interrupted, shutdown time will increase by
  ; max(graceful-shutdown-sec, sleep time)
  (cp/shutdown! internal-thread-pool)

  (heartbeat/stop id redis-conn queue)

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
        internal-thread-pool (cp/threadpool 3)
        random-str (subs (str (random-uuid)) 24 36) ; Take last 12 chars of UUID.
        id (str queue ":" (u/hostname) ":" random-str)
        opts {:id                             id
              :thread-pool                    thread-pool
              :internal-thread-pool           internal-thread-pool
              :redis-conn                     (r/conn redis-url redis-pool-opts)

              :queue                          queue
              :prefixed-queue                 (u/prefix-queue queue)
              :execution-queue                (executor/execution-queue id)

              :graceful-shutdown-sec          graceful-shutdown-sec
              :scheduler-polling-interval-sec scheduler-polling-interval-sec}]

    (cp/future internal-thread-pool (heartbeat/run opts))
    (cp/future internal-thread-pool (scheduler/run opts))
    (cp/future internal-thread-pool (orphan-checker/run opts))

    (dotimes [_ threads]
      (cp/future thread-pool (executor/run opts)))

    (reify Shutdown
      (stop [_] (internal-stop opts)))))
