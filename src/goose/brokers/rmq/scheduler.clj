(ns goose.brokers.rmq.scheduler
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.utils :as u]))

(defn run-at
  [channels schedule job]
  (let [delay (- schedule (u/epoch-time-ms))
        scheduled-job (assoc job :schedule schedule)
        ch (u/random-element channels)]
    (if (neg? delay)
      (rmq-cmds/enqueue-front ch scheduled-job)
      (rmq-cmds/schedule ch scheduled-job delay))))
