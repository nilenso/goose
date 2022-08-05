(ns goose.brokers.broker
  {:no-doc true})

(defmulti new
          (fn [broker & _] (:type broker)))

(defmethod new :default [x & _]
  (throw (ex-info "Invalid broker type" x)))

(defprotocol Broker
  "Protocol for all Goose brokers."
  (enqueue [this job])
  (schedule [this schedule job])
  (start [this worker-opts])

  ; enqueued-jobs API

  ; scheduled-jobs API
  ; dead-jobs API
  )

