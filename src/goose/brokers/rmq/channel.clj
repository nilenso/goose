(ns goose.brokers.rmq.channel
  (:require
    [langohr.basic :as lb]
    [langohr.channel :as lch]
    [langohr.core :as rmq]))

(defn open
  [conn]
  (fn [_]
    (or
      (lch/open conn)
      (throw (Exception. "CHANNEL_MAX limit reached: cannot open new channels")))))

(defn new
  [conn count]
  (doall (map (open conn) (range count))))
