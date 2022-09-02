(ns goose.brokers.rmq.retry
  {:no-doc true}
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.retry]
    [goose.job :as job]
    [goose.utils :as u]

    [langohr.basic :as lb]))

(defn- retry-job
  [{:keys [ch error-service-cfg]}
   {{:keys [retry-delay-sec-fn-sym
            error-handler-fn-sym]} :retry-opts
    {:keys [retry-count]}          :state
    :as                            job}
   ex]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        retry-delay-ms (* ((u/require-resolve retry-delay-sec-fn-sym) retry-count) 1000)
        retry-at (+ retry-delay-ms (u/epoch-time-ms))
        job (assoc-in job [:state :retry-at] retry-at)]
    (u/log-on-exceptions
      (println "****" job)
      (error-handler error-service-cfg job ex))
    (rmq-cmds/schedule ch (job/execution-queue job) job retry-delay-ms)))

(defn wrap-failure
  [next]
  (fn [{:keys [ch delivery-tag] :as opts} job]
    (try
      (next opts job)
      (catch Exception ex
        (let [failed-job (goose.retry/set-failed-config job ex)
              retry-count (get-in failed-job [:state :retry-count])
              max-retries (get-in failed-job [:retry-opts :max-retries] 0)]
          (when (< retry-count max-retries)
            (retry-job opts failed-job ex))
          (lb/ack ch delivery-tag))))))
