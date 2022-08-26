(ns goose.metrics.protocol)

(defprotocol Protocol
  (enabled? [_])
  (gauge [_ metric value tags])
  (increment [_ metric value tags])
  (timing [_ metric duration tags]))
