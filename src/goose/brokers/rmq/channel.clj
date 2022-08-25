(ns goose.brokers.rmq.channel
  (:require
    [langohr.channel :as lch]))

(defn open
  [conn]
  (or
    (lch/open conn)
    (throw (Exception. "CHANNEL_MAX limit reached: cannot open new channels"))))

(defn new
  [conn count]
  (for [_ (range count)]
    (open conn)))
