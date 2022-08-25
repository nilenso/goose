(ns goose.brokers.rmq.channel
  {:no-doc true}
  (:require
    [langohr.channel :as lch]))

(defn open
  [conn]
  (or
    (lch/open conn)
    (throw (Exception. "CHANNEL_MAX limit reached: cannot open new channels"))))

(defn new
  [conn count]
  ; Since `for` returns a lazy-seq; using `doall` to force execution.
  (doall (for [_ (range count)] (open conn))))
