(ns goose.brokers.broker)

(defmulti new
          (fn [broker & _] (:type broker)))

(defmethod new :default [x & _]
  (throw (ex-info "Invalid broker type" x)))
