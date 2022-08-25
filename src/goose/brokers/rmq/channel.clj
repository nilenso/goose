(ns goose.brokers.rmq.channel
  (:require
    [langohr.basic :as lb]
    [langohr.channel :as lch]
    [langohr.core :as rmq]))

(defprotocol ChannelPool
  (get-one [this])
  (set-prefetch-limit [this thread-count])
  (close-all [this]))

(defrecord Channels [channels count]
  ChannelPool
  (get-one [this]
    (nth (:channels this) (rand-int (:count this))))
  (set-prefetch-limit [this thread-count]
    ; Ideally, channels & threads should have a 1-to-1 mapping.
    ; prefetch-limit will be 1 in ideal scenario.
    ; prefetch-limit will be rounded-up when channels < threads.
    ; 5 threads & 2 channels => 3 prefetch-limit.
    (let [limit (int (Math/ceil (/ thread-count (:count this))))]
      (doall (map (fn [ch] (lb/qos ch limit)) (:channels this)))))
  (close-all [this]
    (doall (map (fn [ch] (rmq/close ch)) (:channels this)))))

(defn open
  [conn]
  (fn [_]
    (or
      (lch/open conn)
      (throw (Exception. "CHANNEL_MAX limit reached: cannot open new channels")))))

(defn new
  [conn count]
  (let [channels (map (open conn) (range count))]
    (->Channels channels count)))
