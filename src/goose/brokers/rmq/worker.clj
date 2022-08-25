(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.channel :as rmq-channel]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.dequeuer :as rmq-dequeuer]
    [goose.defaults :as d]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]
    [langohr.consumers :as lc])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- internal-stop
  "Gracefully shuts down the worker threadpool."
  [{:keys [thread-pool consumers graceful-shutdown-sec]}]
  ; Cancel all subscriptions to RabbitMQ.
  (log/warn "Cancelling consumer subscriptions...")
  (doall (map (fn [[ch consumer]] (lb/cancel ch consumer)) @consumers))

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
  [{:keys [rmq-conn queue threads middlewares
           graceful-shutdown-sec]}]
  (let [prefixed-queue (d/prefix-queue queue)
        thread-pool (cp/threadpool threads)
        channels (doall (map (rmq-channel/open rmq-conn) (range threads)))
        consumers (atom '())
        opts {:thread-pool           thread-pool
              :graceful-shutdown-sec graceful-shutdown-sec
              :call                  (chain-middlewares middlewares)
              :prefixed-queue        prefixed-queue
              :consumers             consumers}]
    (rmq-cmds/create-queue (first channels) prefixed-queue)
    (doall (map (fn [ch] (lb/qos ch 1)) channels))

    (dotimes [i threads]
      (let [ch (nth channels i)
            opts (assoc opts :ch ch)
            consumer-tag (lc/subscribe ch prefixed-queue (rmq-dequeuer/handler opts) {:auto-ack false})]
        (swap! consumers #(conj % [ch consumer-tag]))))

    #(internal-stop opts)))
