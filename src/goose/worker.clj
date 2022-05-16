(ns goose.worker
  (:require
    [goose.redis :as r]
    [goose.utils :as u]

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
    ;(r/blpop (:long-polling-timeout-sec opts))
    (car/blpop cfg/default-queue timeout)
    (r/wcar* conn)
    (extract-job)))

(def ^:private thread-pool (atom nil))

(defn- worker [opts]
  (while (not (cp/shutdown? @thread-pool))
    (println "Long-polling broker...")
    (u/log-on-exceptions
      (when-let
        [job (dequeue
               (:redis-conn opts)
               (:long-polling-timeout-sec opts))]
        (execute-job job))))
  (println "Stopped polling broker. Exiting gracefully."))

(defn start
  [opts]
  (let [pool (cp/threadpool (:parallelism opts))]
    (reset! thread-pool pool)
    (doseq [i (range (:parallelism opts))]
      (cp/future pool (worker opts)))))

; QQQ: since start/stop take different options,
; lpop timeout & graceful shutdown time can be different.
(defn stop [opts]
  ; Set state of thread-pool to SHUTDOWN.
  (cp/shutdown @thread-pool)
  ; Execution proceeds immediately after all threads gracefully.
  (.awaitTermination
    @thread-pool
    (:graceful-shutdown-time-sec opts)
    TimeUnit/SECONDS)
  ; Set state of thread-pool to STOP.
  ; Send InterruptedException to close threads.
  (cp/shutdown! @thread-pool))
