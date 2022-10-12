(ns goose.brokers.rmq.retry
  {:no-doc true}
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.defaults :as d]
    [goose.retry]
    [goose.job :as job]
    [goose.utils :as u]))

(defn- retry-job
  [{:keys [ch queue-type publisher-confirms error-service-cfg]}
   {{:keys [retry-delay-sec-fn-sym
            error-handler-fn-sym]} :retry-opts
    {:keys [retry-count]}          :state
    :as                            job}
   ex]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        retry-delay-ms (* ((u/require-resolve retry-delay-sec-fn-sym) retry-count) 1000)
        retry-at (+ retry-delay-ms (u/epoch-time-ms))
        job (assoc-in job [:state :retry-at] retry-at)
        queue-opts (assoc queue-type :queue (job/ready-queue job))]
    (u/log-on-exceptions (error-handler error-service-cfg job ex))
    (rmq-cmds/schedule ch queue-opts publisher-confirms job retry-delay-ms)))

(defn- bury-job
  [{:keys [ch queue-type publisher-confirms error-service-cfg]}
   {{:keys [skip-dead-queue
            death-handler-fn-sym]} :retry-opts
    {:keys [last-retried-at]}      :state
    :as                            job}
   ex]
  (let [death-handler (u/require-resolve death-handler-fn-sym)
        dead-at (or last-retried-at (u/epoch-time-ms))
        job (assoc-in job [:state :dead-at] dead-at)
        queue-opts (assoc queue-type :queue d/prefixed-dead-queue)]
    (u/log-on-exceptions (death-handler error-service-cfg job ex))
    (when-not skip-dead-queue
      (rmq-cmds/enqueue-back ch queue-opts publisher-confirms job))))

(defn wrap-failure
  [next]
  (fn [opts job]
    (try
      (next opts job)
      (catch Exception ex
        (let [failed-job (goose.retry/set-failed-config job ex)
              retry-count (get-in failed-job [:state :retry-count])
              max-retries (get-in failed-job [:retry-opts :max-retries] 0)]
          (if (< retry-count max-retries)
            (retry-job opts failed-job ex)
            (bury-job opts failed-job ex)))))))
