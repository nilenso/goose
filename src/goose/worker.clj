(ns goose.worker
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [goose.utils :as u]
    [goose.validations.worker :refer [validate-worker-params]]

    [clojure.string :as str]
    [com.climate.claypoole :as cp]
    [taoensso.carmine :as car])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- destructure-qualified-fn-sym [fn-sym]
  (as->
    fn-sym s
    (str s)
    (str/split s #"/")
    (map symbol s)))

(defn- execute-job
  [{:keys [id fn-sym args]}]
  (let [[namespace f] (destructure-qualified-fn-sym fn-sym)]
    (require namespace)
    (apply (resolve f) args))
  (println "Executed job-id:" id))

(def ^:private unblocking-queue-prefix
  "goose/unblocking-queue:")

(defn- generate-unblocking-queue []
  (str unblocking-queue-prefix (random-uuid)))

(defn- enqueue-to-unblocking-queue
  [{:keys [redis-conn
           unblocking-queue
           graceful-shutdown-time-sec]}]
  (r/wcar* redis-conn
           (car/lpush unblocking-queue "dummy")
           (car/expire unblocking-queue graceful-shutdown-time-sec)))

(defn- extract-job
  [[queue list-member]]
  (when-not (str/starts-with? queue unblocking-queue-prefix)
    list-member))

(defn- dequeue [conn unblocking-queue]
  (->>
    (car/blpop cfg/default-queue unblocking-queue cfg/long-polling-timeout-sec)
    (r/wcar* conn)
    (extract-job)))

(defn- worker
  [{:keys [thread-pool redis-conn unblocking-queue]}]
  (while (not (cp/shutdown? thread-pool))
    (println "Long-polling broker...")
    (u/log-on-exceptions
      (when-let [job (dequeue redis-conn unblocking-queue)]
        (execute-job job))))
  (println "Stopped polling broker. Exiting gracefully."))

(defprotocol Shutdown
  (stop [_]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [opts]
  (let [thread-pool (:thread-pool opts)]
    ; Set state of thread-pool to SHUTDOWN.
    (cp/shutdown thread-pool)
    ; REASON: TODO
    (enqueue-to-unblocking-queue opts)
    ; Wait until all threads exit gracefully.
    (.awaitTermination
      thread-pool
      (:graceful-shutdown-time-sec opts)
      TimeUnit/SECONDS)
    ; Set state of thread-pool to STOP.
    ; Send InterruptedException to close threads.
    (cp/shutdown! thread-pool)))

(defn start
  "Starts a threadpool for worker."
  [{:keys [redis-url
           redis-pool-opts
           graceful-shutdown-time-sec
           parallelism]
    :or   {redis-url                  cfg/default-redis-url
           redis-pool-opts            {}
           graceful-shutdown-time-sec 30
           parallelism                1}}]
  (validate-worker-params
    redis-url
    redis-pool-opts
    graceful-shutdown-time-sec
    parallelism)
  (let [thread-pool (cp/threadpool parallelism)
        opts {:redis-conn                 (r/conn redis-url redis-pool-opts)
              :thread-pool                thread-pool
              :graceful-shutdown-time-sec graceful-shutdown-time-sec
              ; REASON: TODO: github-issue
              :unblocking-queue           (generate-unblocking-queue)}]
    (doseq [i (range parallelism)]
      (cp/future thread-pool (worker opts)))
    (reify Shutdown
      (stop [_] (internal-stop opts)))))
