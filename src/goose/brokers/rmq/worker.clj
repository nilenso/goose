(ns goose.brokers.rmq.worker
  {:no-doc true}
  (:require
    [goose.brokers.rmq.channel :as rmq-channel]
    [goose.brokers.rmq.consumer :as rmq-consumer]
    [goose.brokers.rmq.queue :as rmq-queue]
    [goose.brokers.rmq.retry :as rmq-retry]
    [goose.defaults :as d]
    [goose.job :as job]
    [goose.metrics.middleware :as metrics-middleware]
    [goose.worker :as goose-worker]

    [clojure.tools.logging :as log]
    [com.climate.claypoole :as cp]
    [langohr.basic :as lb]
    [langohr.core :as lcore]))

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
  (lcore/close rmq-conn)

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
  [{:keys [threads settings queue queue-type middlewares publisher-confirms return-listener-fn shutdown-listener-fn]
    :as   common-opts}]
  (let [thread-pool (cp/threadpool threads)
        settings (assoc settings :executor thread-pool)
        conn (lcore/connect settings)
        channels (rmq-channel/new-pool conn threads publisher-confirms return-listener-fn)
        ready-queue (d/prefix-queue queue)

        rmq-opts {:rmq-conn    conn
                  :channels    channels
                  :thread-pool thread-pool
                  :call        (chain-middlewares middlewares)
                  :ready-queue ready-queue}
        opts (merge rmq-opts common-opts)
        opts (dissoc opts :return-listener-fn :queue :middlewares :broker :settings)]
    (lcore/add-shutdown-listener conn shutdown-listener-fn)

    ; A queue must exist before consumers can subscribe to it.
    (let [queue-opts (assoc queue-type :queue ready-queue)]
      (rmq-queue/declare (first channels) queue-opts))

    (let [consumers (rmq-consumer/run opts)]
      (reify goose-worker/Shutdown
        (stop [_] (internal-stop (assoc opts :ch+consumers consumers)))))))
