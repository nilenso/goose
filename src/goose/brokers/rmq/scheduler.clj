(ns goose.brokers.rmq.scheduler
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.utils :as u]))

(defn run-at
  [ch confirms schedule job]
  (let [delay (- schedule (u/epoch-time-ms))
        scheduled-job (assoc job :schedule schedule)]
    (if (neg? delay)
      (rmq-cmds/enqueue-front ch confirms scheduled-job)
      (rmq-cmds/schedule ch confirms scheduled-job delay))))
