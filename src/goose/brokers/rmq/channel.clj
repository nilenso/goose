(ns ^:no-doc goose.brokers.rmq.channel
  (:require
   [goose.brokers.rmq.return-listener :as return-listener]
   [goose.defaults :as d]

   [langohr.basic :as lb]
   [langohr.channel :as lch]
   [langohr.confirm :as lcnf]))

(defn open
  [conn
   {:keys [strategy ack-handler nack-handler]}
   return-listener]
  (let [ch (lch/open conn)]
    (if ch
      (let [ch-number (.getChannelNumber ch)]
        (condp = strategy
          d/sync-confirms
          (lcnf/select ch)

          d/async-confirms
          (lcnf/select ch #(ack-handler ch-number %1 %2) #(nack-handler ch-number %1 %2)))
        (lb/add-return-listener ch (return-listener/wrapper return-listener))
        ch)

      (throw (ex-info "CHANNEL_MAX limit reached: cannot open new channels" {:rmq-conn conn})))))

(defn new-pool
  [conn size publisher-confirms return-listener]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range size)] (open conn publisher-confirms return-listener))))
