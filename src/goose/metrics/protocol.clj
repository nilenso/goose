(ns goose.metrics.protocol)

(defprotocol Protocol
  (enabled? [_])
  (gauge [_ key value tags])
  (increment [_ key value tags])
  (timing [_ key duration tags]))
