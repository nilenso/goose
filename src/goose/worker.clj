(ns goose.worker
  (:require
    [goose.config :as cfg]
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
  [{:keys [redis-conn prefixed-queues unblocking-queue]}]
  (let [queues-superset (conj prefixed-queues unblocking-queue)]
    (extract-job (r/dequeue redis-conn queues-superset))))

(defn- worker
  [opts]
  (while (not (cp/shutdown? (:thread-pool opts)))
    (log/info "Long-polling broker...")
    (u/log-on-exceptions
      (when-let [job (pop-job opts)]
        (execute-job job))))
  (log/info "Stopped polling broker. Exiting gracefully..."))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [thread-pool redis-conn
           unblocking-queue graceful-shutdown-time-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (cp/shutdown thread-pool)
  ; REASON: https://github.com/nilenso/goose/issues/14
  (r/enqueue-with-expiry
    redis-conn unblocking-queue "dummy" graceful-shutdown-time-sec)
  ; Wait until all threads exit gracefully.
  (.awaitTermination
    thread-pool
    graceful-shutdown-time-sec
    TimeUnit/SECONDS)
  ; Set state of thread-pool to STOP.
  ; Send InterruptedException to close threads.
  (cp/shutdown! thread-pool))

(defn start
  "Starts a threadpool for worker."
  [{:keys [redis-url
           redis-pool-opts
           queues
           graceful-shutdown-time-sec
           parallelism]
    :or   {redis-url                  cfg/default-redis-url
           redis-pool-opts            {}
           queues                     [cfg/default-queue]
           graceful-shutdown-time-sec 30
           parallelism                1}}]
  (validate-worker-params
    redis-url
    redis-pool-opts
    queues
    graceful-shutdown-time-sec
    parallelism)
  (let [thread-pool (cp/threadpool parallelism)
        opts {:redis-conn                 (r/conn redis-url redis-pool-opts)
              :thread-pool                thread-pool
              :graceful-shutdown-time-sec graceful-shutdown-time-sec
              :prefixed-queues            (map #(str cfg/queue-prefix %) queues)
              ; Reason for having a utility unblocking queue:
              ; https://github.com/nilenso/goose/issues/14
              :unblocking-queue           (generate-unblocking-queue)}]
    (doseq [_ (range parallelism)]
      (cp/future thread-pool (worker opts)))
    (reify Shutdown
      (stop [_] (internal-stop opts)))))
