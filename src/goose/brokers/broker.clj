(ns goose.brokers.broker
  {:no-doc true})

(defmulti new
          (fn [broker & _] (:type broker)))

(defmethod new :default [x & _]
  (throw (ex-info "Invalid broker type" x)))
