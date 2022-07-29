(ns goose.worker
  (:require
    [goose.brokers.broker :as broker]
    [goose.brokers.redis.client :as redis-client]
    [goose.defaults :as d]
    [goose.executor :as executor]
    [goose.heartbeat :as heartbeat]
    [goose.job :as job]
    [goose.orphan-checker :as orphan-checker]
    [goose.retry :as retry]
    [goose.scheduler :as scheduler]
    [goose.statsd :as statsd]
    [goose.utils :as u]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp])
  (:import
    [java.util.concurrent TimeUnit]))

(defprotocol Shutdown
  "Shutdown a worker object."
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [thread-pool internal-thread-pool graceful-shutdown-sec]
    :as   opts}]
  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)
  (cp/shutdown internal-thread-pool)

  ; Interrupt scheduler to exit sleep & terminate thread-pool.
  ; If not interrupted, shutdown time will increase by
  ; max(graceful-shutdown-sec, sleep time)
  (cp/shutdown! internal-thread-pool)

  ; Give jobs executing grace time to complete.
  (log/warn "Awaiting executing jobs to complete.")

  (.awaitTermination
    thread-pool
    graceful-shutdown-sec
    TimeUnit/SECONDS)

  (heartbeat/stop opts)

  ; Set state of thread-pool to STOP.
  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(defonce default-opts
         {:broker-opts                    redis-client/default-opts
          :threads                        1
          :queue                          d/default-queue
          :scheduler-polling-interval-sec 5
          :graceful-shutdown-sec          30
          :middlewares                    nil
          :error-service-cfg              nil
          :statsd-opts                    statsd/default-opts})

(defn- chain-middlewares
  [middlewares]
  (let [call (if middlewares
               (-> executor/execute-job (middlewares))
               executor/execute-job)]
    (-> call
        (statsd/wrap-metrics)
        (job/wrap-latency)
        (retry/wrap-failure))))

(defn start
  "Starts a threadpool for worker."
  [{:keys [threads broker-opts statsd-opts queue
           error-service-cfg scheduler-polling-interval-sec
           middlewares graceful-shutdown-sec]}]
  (let [thread-pool (cp/threadpool threads)
        ; Internal threadpool for scheduler, orphan-checker & heartbeat.
        internal-thread-pool (cp/threadpool d/internal-thread-pool-size)
        random-str (subs (str (random-uuid)) 24 36) ; Take last 12 chars of UUID.
        id (str queue ":" (u/hostname) ":" random-str)
        call (chain-middlewares middlewares)
        opts {:id                             id
              :thread-pool                    thread-pool
              :internal-thread-pool           internal-thread-pool
              :redis-conn                     (broker/new broker-opts threads)
              :call                           call
              :error-service-cfg              error-service-cfg
              :statsd-opts                    statsd-opts

              :process-set                    (str d/process-prefix queue)
              :prefixed-queue                 (d/prefix-queue queue)
              :in-progress-queue              (executor/preservation-queue id)

              :graceful-shutdown-sec          graceful-shutdown-sec
              :scheduler-polling-interval-sec scheduler-polling-interval-sec}]

    (statsd/initialize statsd-opts)

    (cp/future internal-thread-pool (statsd/run opts))
    (cp/future internal-thread-pool (heartbeat/run opts))
    (cp/future internal-thread-pool (scheduler/run opts))
    (cp/future internal-thread-pool (orphan-checker/run opts))

    (dotimes [_ threads]
      (cp/future thread-pool (executor/run opts)))

    (reify Shutdown
      (stop [_] (internal-stop opts)))))
