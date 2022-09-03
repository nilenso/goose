(ns goose.brokers.rmq.channel
  {:no-doc true}
  (:require
    [langohr.channel :as lch]
    [langohr.confirm :as lcnf]
    [goose.brokers.rmq.publisher-confirms :as rmq-publisher-confirms]))

(defn open
  [conn {:keys [strategy ack-handler nack-handler]}]
  (let [ch (lch/open conn)]
    (if ch
      (condp = strategy
        rmq-publisher-confirms/sync
        (lcnf/select ch)

        rmq-publisher-confirms/async
        (lcnf/select ch ack-handler nack-handler))
      (throw (ex-info "CHANNEL_MAX limit reached: cannot open new channels" {:rmq-conn conn})))
    ch))

(defn new-pool
  [conn size confirms]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range size)] (open conn confirms))))
