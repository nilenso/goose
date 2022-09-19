(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.channel :as rmq-channel]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.consumer :as rmq-consumer]
    [goose.brokers.rmq.retry :as rmq-retry]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.metrics.middleware :as metrics-middleware]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [langohr.basic :as lb])
  (:import
    [java.util.concurrent TimeUnit]))

(defn- internal-stop
  [{:keys [thread-pool graceful-shutdown-sec ch+consumers]}]
  ; Cancel all subscriptions to RabbitMQ.
  (log/warn "Cancelling consumer subscriptions...")
  (doall (for [[ch consumer] ch+consumers] (lb/cancel ch consumer)))

  ; Set state of thread-pool to SHUTDOWN.
  (log/warn "Shutting down thread-pool...")
  (cp/shutdown thread-pool)

  ; Give jobs executing grace time to complete.
  (log/warn "Awaiting executing jobs to complete.")

  (.awaitTermination thread-pool graceful-shutdown-sec TimeUnit/SECONDS)

  ; Set state of thread-pool to STOP.
  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(defn- chain-middlewares
  [middlewares]
  (let [call (if middlewares
               (-> rmq-consumer/execute-job (middlewares))
               rmq-consumer/execute-job)]
    (-> call
        (metrics-middleware/wrap-metrics)
        (job/wrap-latency)
        (rmq-retry/wrap-failure))))

(defn start
  [{:keys [rmq-conn queue-type publisher-confirms queue threads middlewares] :as common-opts}]
  (let [ready-queue (d/prefix-queue queue)
        thread-pool (cp/threadpool threads)
        channels (rmq-channel/new-pool rmq-conn threads publisher-confirms)
        rmq-opts {:thread-pool thread-pool
                  :call        (chain-middlewares middlewares)
                  :ready-queue ready-queue
                  :channels    channels}
        opts (merge rmq-opts common-opts)]
    ; A queue must exist before consumers can subscribe to it.
    (let [queue-opts (assoc queue-type :queue ready-queue)]
      (rmq-cmds/create-queue-and-exchanges (first channels) queue-opts))

    (let [consumers (rmq-consumer/run opts)]
      #(internal-stop (assoc opts :ch+consumers consumers)))))
