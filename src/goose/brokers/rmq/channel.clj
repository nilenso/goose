(ns goose.brokers.rmq.channel
  {:no-doc true}
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]

    [langohr.channel :as lch]
    [langohr.confirm :as lcnf]))

(defn open
  [conn {:keys [strategy ack-handler nack-handler]}]
  (let [ch (lch/open conn)]
    (if ch
      (condp = strategy
        d/sync-confirms
        (lcnf/select ch)

        d/async-confirms
        (lcnf/select ch (u/require-resolve ack-handler) (u/require-resolve nack-handler)))

      (throw (ex-info "CHANNEL_MAX limit reached: cannot open new channels" {:rmq-conn conn})))
    ch))

(defn new-pool
  [conn size publisher-confirms]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range size)] (open conn publisher-confirms))))
