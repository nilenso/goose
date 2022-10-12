(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.connection :as rmq-connection]
    [goose.brokers.rmq.consumer :as rmq-consumer]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.brokers.rmq.retry :as rmq-retry]
    [goose.consumer :as consumer]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.metrics.middleware :as metrics-middleware]
    [goose.worker :as worker]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]))

(defn- await-execution
  [thread-pool graceful-shutdown-sec]
  (when-not (zero? (.getActiveCount thread-pool))
    (Thread/sleep 1000)
    #(await-execution thread-pool (dec graceful-shutdown-sec))))

(defn- internal-stop
  [{:keys [rmq-conn thread-pool graceful-shutdown-sec ch+consumers]}]
  (log/warn "Cancelling consumer subscriptions...")
  (doall (for [[ch consumer] ch+consumers] (lb/cancel ch consumer)))

  ; RabbitMQ thread-pool & conn closing is a catch-22 situation.
  ; If thread-pool is shutdown before conn,
  ; RejectedExecutionException is thrown because conn uses
  ; executor-service thread to close the connection.
  ; If conn is closed before thread-pool, ACKs for completed jobs
  ; won't reach RabbitMQ.
  ; Hence, the ugly sleep using `.getActiveCount`
  ; Ideally, thread-pools should be closed as
  ; implemented in `goose.brokers.redis.worker/internal-stop`
  (log/warn "Awaiting in-progress jobs to complete.")
  (trampoline await-execution thread-pool graceful-shutdown-sec)

  ; Channels get closed automatically when connection is closed.
  (log/warn "Closing RabbitMQ connection")
  (rmq-connection/close rmq-conn)

  (log/warn "Sending InterruptedException to close threads.")
  (cp/shutdown! thread-pool))

(defn- chain-middlewares
  [middlewares]
  (let [call (if middlewares
               (-> consumer/execute-job (middlewares))
               consumer/execute-job)]
    (-> call
        (metrics-middleware/wrap-metrics)
        (job/wrap-latency)
        (rmq-retry/wrap-failure)
        (rmq-consumer/wrap-acks))))

(defn start
  [{:keys [threads queue queue-type middlewares]
    :as   common-opts}]
  (let [thread-pool (cp/threadpool threads)
        common-opts (assoc-in common-opts [:settings :executor] thread-pool)
        [rmq-conn channels] (rmq-connection/open common-opts threads)
        ready-queue (d/prefix-queue queue)

        rmq-opts {:rmq-conn    rmq-conn
                  :channels    channels
                  :thread-pool thread-pool
                  :call        (chain-middlewares middlewares)
                  :ready-queue ready-queue}
        opts (merge rmq-opts common-opts)
        opts (dissoc opts :threads :queue :middlewares :broker :return-listener :shutdown-listener :settings)]

    ; A queue must exist before consumers can subscribe to it.
    (let [queue-opts (assoc queue-type :queue ready-queue)]
      (rmq-queue/declare (first channels) queue-opts))

    (let [consumers (rmq-consumer/run opts)]
      (reify worker/Shutdown
        (stop [_] (internal-stop (assoc opts :ch+consumers consumers)))))))
