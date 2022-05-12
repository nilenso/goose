(ns goose.worker
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [goose.utils :as u]
    [taoensso.carmine :as car]
    [clojure.string :as string]
    [com.climate.claypoole :as cp])
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
  (let
    [[namespace f] (destructure-qualified-fn-sym fn-sym)]
    (require namespace)
    (apply
      (resolve f)
      args))
  (println "Executed job-id:" id))

(def ^:private long-polling-timeout
  "Blocking calls using Carmine library swallow InterruptedException.
  We've set timeout to 30% of configured graceful shutdown time.
  This allocates function 70% time to complete in worst case.
  Issue link: TODO"
  (quot cfg/graceful-shutdown-time-sec 3))

(defn- extract-job
  [list-member]
  (second list-member))

(defn- dequeue []
  (->
    cfg/default-queue
    (car/blpop long-polling-timeout)
    (r/wcar*)
    (extract-job)))

(def ^:private thread-pool (atom nil))

(defn- worker []
  (while (not (cp/shutdown? @thread-pool))
    (println "Long-polling broker...")
    (u/log-on-exceptions
      (when-let
        [job (dequeue)]
        (execute-job job))))
  (println "Stopped polling broker. Exiting gracefully."))

(defn start
  [parallelism]
  (def pool (cp/threadpool parallelism))
  (reset! thread-pool pool)
  (doseq [i (range parallelism)]
    (cp/future pool (worker))))

(defn stop []
  ; Set state of thread-pool to SHUTDOWN.
  (cp/shutdown @thread-pool)
  ; Execution proceeds immediately after all threads gracefully.
  (.awaitTermination @thread-pool cfg/graceful-shutdown-time-sec TimeUnit/SECONDS)
  ; Set state of thread-pool to STOP.
  ; Send InterruptedException to close threads.
  (cp/shutdown! @thread-pool))
