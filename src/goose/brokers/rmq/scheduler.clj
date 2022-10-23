(ns ^:no-doc goose.brokers.rmq.scheduler
  (:require
    [goose.brokers.rmq.commands :as rmq-cmds]
    [goose.utils :as u]))

(defn run-at
  [ch queue-opts publisher-confirms schedule job]
  (let [delay-ms (- schedule (u/epoch-time-ms))
        scheduled-job (assoc job :schedule-run-at schedule)]
    (if (neg? delay-ms)
      (rmq-cmds/enqueue-front ch queue-opts publisher-confirms scheduled-job)
      (rmq-cmds/schedule ch queue-opts publisher-confirms scheduled-job delay-ms))))
