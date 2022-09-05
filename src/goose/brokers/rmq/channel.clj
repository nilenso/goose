(ns goose.brokers.rmq.channel
  {:no-doc true}
  (:require
    [langohr.channel :as lch]))

(defn open
  [conn]
  (or
    (lch/open conn)
    (throw (ex-info "CHANNEL_MAX limit reached: cannot open new channels" {:rmq-conn conn}))))

(defn new-pool
  [conn size]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range size)] (open conn))))
