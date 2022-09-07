(ns goose.brokers.rmq.retry
  {:no-doc true}
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.retry]
    [goose.utils :as u]

    [langohr.basic :as lb]
    [goose.defaults :as d]))

(defn- retry-job
  [{:keys [ch publisher-confirms error-service-cfg]}
   {{:keys [retry-delay-sec-fn-sym
            error-handler-fn-sym]} :retry-opts
    {:keys [retry-count]}          :state
    :as                            job}
   ex]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        retry-delay-ms (* ((u/require-resolve retry-delay-sec-fn-sym) retry-count) 1000)
        retry-at (+ retry-delay-ms (u/epoch-time-ms))
        job (assoc-in job [:state :retry-at] retry-at)]
    (u/log-on-exceptions (error-handler error-service-cfg job ex))
    (rmq-cmds/schedule ch publisher-confirms job retry-delay-ms)))

(defn- bury-job
  [{:keys [ch publisher-confirms error-service-cfg]}
   {{:keys [skip-dead-queue
            death-handler-fn-sym]} :retry-opts
    {:keys [last-retried-at]}      :state
    :as                            job}
   ex]
  (let [death-handler (u/require-resolve death-handler-fn-sym)
        dead-at (or last-retried-at (u/epoch-time-ms))
        job (assoc-in job [:state :dead-at] dead-at)]
    (u/log-on-exceptions (death-handler error-service-cfg job ex))
    (when-not skip-dead-queue
      (rmq-cmds/enqueue-back ch publisher-confirms job d/prefixed-dead-queue))))

(defn wrap-failure
  [next]
  (fn [{:keys [ch delivery-tag] :as opts} job]
    (try
      (next opts job)
      (catch Exception ex
        (let [failed-job (goose.retry/set-failed-config job ex)
              retry-count (get-in failed-job [:state :retry-count])
              max-retries (get-in failed-job [:retry-opts :max-retries] 0)]
          (if (< retry-count max-retries)
            (retry-job opts failed-job ex)
            (bury-job opts failed-job ex))
          (lb/ack ch delivery-tag))))))
