(ns goose.brokers.rmq.scheduler
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.defaults :as d]
    [goose.utils :as u]))

(defn run-at
  [channels schedule job]
  (let [delay (- schedule (u/epoch-time-ms))
        scheduled-job (assoc job :schedule schedule)
        ch (u/random-element channels)]
    (when (< d/rmq-delay-limit-ms delay)
      (throw (ex-info "MAX_DELAY limit breached: 2^32 ms(~49 days 17 hours)" {})))
    (if (neg? delay)
      (rmq-cmds/enqueue-front ch scheduled-job)
      (rmq-cmds/schedule ch scheduled-job delay))))
