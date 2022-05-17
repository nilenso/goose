(ns goose.worker
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [goose.utils :as u]
    [goose.validations.worker :refer [validate-worker-params]]

    [clojure.string :as string]
    [com.climate.claypoole :as cp]
    [taoensso.carmine :as car])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- destructure-qualified-fn-sym [fn-sym]
  (as->
    fn-sym s
    (str s)
    (string/split s #"/")
    (map symbol s)))

(defn- execute-job
  [{:keys [id fn-sym args]}]
  (let [[namespace f] (destructure-qualified-fn-sym fn-sym)]
    (require namespace)
    (apply
      (resolve f)
      args))
  (println "Executed job-id:" id))

(defn- extract-job
  [list-member]
  (second list-member))

(defn- dequeue [conn timeout]
  (->>
    (car/blpop cfg/default-queue timeout)
    (r/wcar* conn)
    (extract-job)))

(defn- worker
  [{:keys [thread-pool redis-conn long-polling-timeout-sec]}]
  (while (not (cp/shutdown? thread-pool))
    (println "Long-polling broker...")
    (u/log-on-exceptions
      (when-let
        [job (dequeue
               redis-conn
               long-polling-timeout-sec)]
        (execute-job job))))
  (println "Stopped polling broker. Exiting gracefully."))

(defn worker-opts
  "Configures options for worker."
  [& {:keys [redis-url
             redis-pool-opts
             graceful-shutdown-time-sec
             parallelism]
      :or   {redis-url                  "redis://localhost:6379"
             redis-pool-opts            {}
             graceful-shutdown-time-sec 30
             parallelism                1}}]
  (let [opts
        {:redis-conn                 (r/conn redis-url redis-pool-opts)
         :graceful-shutdown-time-sec graceful-shutdown-time-sec
         ; Long polling timeout is set to 30% of graceful shutdown time.
         ; REASON: TODO: github-issue
         :long-polling-timeout-sec   (quot graceful-shutdown-time-sec 3)
         :parallelism                parallelism}]
    (println "INFO: Goose worker options:\n" opts)
    opts))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool.
  `stop` fn MUST be called with same opts as `start` fn."
  [{:keys [thread-pool graceful-shutdown-time-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (cp/shutdown thread-pool)
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
  [opts]
  (validate-worker-params opts)
  (let [thread-pool (cp/threadpool (:parallelism opts))
        internal-opts (assoc opts :thread-pool thread-pool)]
    (doseq [i (range (:parallelism opts))]
      (cp/future thread-pool (worker internal-opts)))
    (reify Shutdown
      (stop [_] (internal-stop internal-opts)))))
