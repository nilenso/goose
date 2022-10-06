(ns goose.brokers.rmq.channel
  {:no-doc true}
  (:require
    [goose.brokers.rmq.return-listener :as return-listener]
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.basic :as lb]
    [langohr.channel :as lch]
    [langohr.confirm :as lcnf]))


(defn open
  [conn {:keys [strategy ack-handler nack-handler]} return-listener-fn]
  (let [ch (lch/open conn)]
    (if ch
      (do
        (condp = strategy
          d/sync-confirms
          (lcnf/select ch)

          d/async-confirms
          (lcnf/select ch (u/require-resolve ack-handler) (u/require-resolve nack-handler)))
        (lb/add-return-listener ch (return-listener/wrapper return-listener-fn))
        ch)

      (throw (ex-info "CHANNEL_MAX limit reached: cannot open new channels" {:rmq-conn conn})))))

(defn new-pool
  [conn size publisher-confirms return-listener-fn]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range size)] (open conn publisher-confirms return-listener-fn))))
