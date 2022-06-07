(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

(def retry-schedule-queue (u/prefix-queue d/schedule-queue))
(def dead-queue (u/prefix-queue d/dead-queue))

(defn default-error-handler
  [job ex]
  (log/error ex "Job execution failed." job))

(defn default-death-handler
  [job ex]
  (log/error ex "Job retries exhausted." job))

(defn default-retry-delay-sec
  [retry-count]
  (+ 20
     (* (rand-int 20) (inc retry-count))
     (reduce * (repeat 4 retry-count)))) ; retry-count^4

(def default-opts
  {:max-retries            27
   :retry-delay-sec-fn-sym `default-retry-delay-sec
   :retry-queue            nil
   :error-handler-fn-sym   `default-error-handler
   :skip-dead-queue        false
   :death-handler-fn-sym   `default-death-handler})

(defn- prefix-retry-queue-if-present
  [retry-opts]
  (if-let [retry-queue (:retry-queue retry-opts)]
    (assoc retry-opts :retry-queue (u/prefix-queue retry-queue))
    retry-opts))

(defn set-retry-opts
  [opts retry-opts]
  (let [prefixed-opts (prefix-retry-queue-if-present retry-opts)
        merged-opts (merge default-opts prefixed-opts)]
    (assoc opts :retry-opts merged-opts)))

(defn- failed-job-dynamic-config
  [{{:keys [retry-count]} :dynamic-config
    :keys                 [dynamic-config]}
   ex]
  (if retry-count
    ; Job has failed before.
    (assoc dynamic-config
      :error ex
      :last-retried-at (u/epoch-time-ms)
      :retry-count (inc retry-count))

    ; Job has failed for the first time.
    (assoc dynamic-config
      :error ex
      :first-failed-at (u/epoch-time-ms)
      :retry-count 0)))

(defn- set-failed-config
  [job ex]
  (assoc
    job :dynamic-config
        (failed-job-dynamic-config job ex)))

(defn- retry-job
  [redis-conn {{:keys [retry-delay-sec-fn-sym
                       error-handler-fn-sym]} :retry-opts
               {:keys [retry-count]}          :dynamic-config
               :as                            job}
   ex]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        retry-delay-sec ((u/require-resolve retry-delay-sec-fn-sym) retry-count)
        retry-at (u/add-sec retry-delay-sec)]
    (u/log-on-exceptions (error-handler job ex))
    (r/enqueue-sorted-set redis-conn retry-schedule-queue retry-at job)))

(defn- bury-job
  [redis-conn {{:keys [last-retried-at skip-dead-queue
                       death-handler-fn-sym]} :retry-opts
               :as                                  job} ex]
  (let [death-handler (u/require-resolve death-handler-fn-sym)
        dead-at (or last-retried-at (u/epoch-time-ms))]
    (u/log-on-exceptions (death-handler job ex))
    (when-not skip-dead-queue
      (r/enqueue-sorted-set redis-conn dead-queue dead-at job))))

(defn handle-failure
  [redis-conn job ex]
  (let [failed-job (set-failed-config job ex)
        retry-count (get-in failed-job [:dynamic-config :retry-count])
        max-retries (get-in failed-job [:retry-opts :max-retries])]
    (if (< retry-count max-retries)
      (retry-job redis-conn failed-job ex)
      (bury-job redis-conn failed-job ex))))
