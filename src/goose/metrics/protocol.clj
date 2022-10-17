(ns goose.metrics.protocol)

(defprotocol Protocol
  (enabled? [this])
  (gauge [this key value tags])
  (increment [this key value tags])
  (timing [this key duration tags]))
