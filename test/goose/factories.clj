(ns goose.factories
  (:require [goose.brokers.redis.commands :as redis-cmds]
            [goose.brokers.rmq.commands :as rmq-cmds]
            [goose.brokers.redis.cron :as cron]
            [goose.brokers.redis.scheduler :as scheduler]
            [goose.defaults :as d]
            [goose.job :as j]
            [goose.retry :as retry]
            [goose.test-utils :as tu]
            [goose.utils :as u]))

(defn job [overrides]
  (merge (j/new `tu/my-fn (list "foobar") tu/queue (d/prefix-queue tu/queue) retry/default-opts)
         overrides))

(defn job-description [overrides]
  (merge (j/description `tu/my-fn (list "foobar") tu/queue (d/prefix-queue tu/queue) retry/default-opts)
         overrides))

(defn cron-opts [overrides]
  (merge {:cron-name     (str (random-uuid))
          :cron-schedule "*/3 * * * *"
          :timezone      "US/Pacific"} overrides))

(defn create-async-job-in-redis [& [overrides]]
  (let [j (job overrides)]
    (redis-cmds/enqueue-back tu/redis-conn (:ready-queue j) j)
    (:id j)))

(defn create-schedule-job-in-redis [& [overrides]]
  (let [{:keys [scheduled-at] :as j} (job (merge {:scheduled-at (+ (u/epoch-time-ms) 1000000)} overrides))]
    (scheduler/run-at tu/redis-conn scheduled-at j)
    (:id j)))

(defn create-periodic-job-in-redis [& [overrides]]
  (let [job-desc (job-description (:job-description overrides))
        cron-opts (cron-opts (:cron-opts overrides))]
    (cron/register tu/redis-conn cron-opts job-desc)
    cron-opts))

(defn create-dead-job-in-redis [& [overrides]]
  (let [now (u/epoch-time-ms)
        error-state {:state {:error           "Error"
                             :last-retried-at now
                             :died-at         now
                             :first-failed-at 1701344365468
                             :retry-count     27
                             :retry-at        1701344433359}}
        j (job (merge error-state overrides))]
    (redis-cmds/enqueue-sorted-set tu/redis-conn d/prefixed-dead-queue
                                   (get-in j [:state :died-at]) j)
    (:id j)))

(defn create-jobs-in-redis [{:keys [enqueued scheduled periodic dead]
                    :or   {enqueued 0 scheduled 0 periodic 0 dead 0}} & [overrides]]
  (let [apply-fn-n-times (fn [n f & args]
                           (dotimes [_ n] (apply f args)))]
    (apply-fn-n-times enqueued create-async-job-in-redis (:enqueued overrides))
    (apply-fn-n-times scheduled create-schedule-job-in-redis (:scheduled overrides))
    (apply-fn-n-times periodic create-periodic-job-in-redis (:periodic overrides))
    (apply-fn-n-times dead create-dead-job-in-redis (:dead overrides))))

