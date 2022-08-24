(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.channels :as channels]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.dequeuer :as rmq-dequeuer]
    [goose.defaults :as d]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [thread-pool graceful-shutdown-sec]}]
  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)

  ; Give jobs executing grace time to complete.
  (log/warn "Awaiting executing jobs to complete.")

  (.awaitTermination
    thread-pool
    graceful-shutdown-sec
    TimeUnit/SECONDS)

  ; Set state of thread-pool to STOP.
  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(defn- chain-middlewares
  [middlewares]
  (let [call (if middlewares
               (-> rmq-dequeuer/execute-job (middlewares))
               rmq-dequeuer/execute-job)]
    call))

(defn start
  [{:keys [pool queue threads middlewares
           graceful-shutdown-sec]}]
  (let [prefixed-queue (d/prefix-queue queue)
        thread-pool (cp/threadpool threads)
        channels (:channels pool)
        opts {:thread-pool           thread-pool
              :graceful-shutdown-sec graceful-shutdown-sec
              :call                  (chain-middlewares middlewares)}]
    (rmq-cmds/create-queue (first channels) prefixed-queue)
    (channels/set-prefetch-limit pool threads)

    (dotimes [i threads]
      (let [ch (nth channels (mod i (:count pool)))
            opts (assoc opts :ch ch)]
        (cp/future thread-pool (rmq-dequeuer/run opts))))

    #(internal-stop opts)))
