(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.channel :as rmq-channel]
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.brokers.rmq.consumer :as rmq-consumer]
    [goose.brokers.rmq.retry :as rmq-retry]
    [goose.defaults :as d]

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
        (rmq-retry/wrap-failure))))

(defn start
  [{:keys [rmq-conn publisher-confirms queue threads middlewares] :as common-opts}]
  (let [prefixed-queue (d/prefix-queue queue)
        thread-pool (cp/threadpool threads)
        channels (rmq-channel/new-pool rmq-conn threads publisher-confirms)
        rmq-opts {:thread-pool    thread-pool
                  :call           (chain-middlewares middlewares)
                  :prefixed-queue prefixed-queue
                  :channels       channels}
        opts (merge rmq-opts common-opts)]
    (rmq-cmds/create-queue-and-exchanges (first channels) prefixed-queue)

    (let [consumers (rmq-consumer/run opts)]
      #(internal-stop (assoc opts :ch+consumers consumers)))))
