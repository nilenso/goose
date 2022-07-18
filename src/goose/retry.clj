(ns goose.retry
  (:require
    [goose.defaults :as d]
    [goose.redis :as r]
    [goose.utils :as u]

    [clojure.tools.logging :as log]))

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

(defonce default-opts
         {:max-retries            27
          :retry-delay-sec-fn-sym `default-retry-delay-sec
          :retry-queue            nil
          :error-handler-fn-sym   `default-error-handler
          :skip-dead-queue        false
          :death-handler-fn-sym   `default-death-handler})

(defn- prefix-retry-queue-if-present
  [retry-opts]
  (if-let [retry-queue (:retry-queue retry-opts)]
    (assoc retry-opts :retry-queue (d/prefix-queue retry-queue))
    retry-opts))

(defn prefix-queue-if-present
  [opts]
  (->> opts
       (prefix-retry-queue-if-present)
       (merge default-opts)))

(defn- failure-state
  [{{:keys [retry-count first-failed-at]} :state} ex]
  {:error           ex
   :last-retried-at (when first-failed-at (u/epoch-time-ms))
   :first-failed-at (or first-failed-at (u/epoch-time-ms))
   :retry-count     (if retry-count (inc retry-count) 0)})

(defn- set-failed-config
  [job ex]
  (assoc
    job :state
        (failure-state job ex)))

(defn- retry-job
  [redis-conn {{:keys [retry-delay-sec-fn-sym
                       error-handler-fn-sym]} :retry-opts
               {:keys [retry-count]}          :state
               :as                            job}
   ex]
  (let [error-handler (u/require-resolve error-handler-fn-sym)
        retry-delay-sec ((u/require-resolve retry-delay-sec-fn-sym) retry-count)
        retry-at (u/add-sec retry-delay-sec)
        job (assoc-in job [:state :retry-at] retry-at)]
    (u/log-on-exceptions (error-handler job ex))
    (r/enqueue-sorted-set redis-conn d/prefixed-retry-schedule-queue retry-at job)))

(defn- bury-job
  [redis-conn
   {{:keys [skip-dead-queue
            death-handler-fn-sym]} :retry-opts
    {:keys [last-retried-at]}      :state
    :as                            job}
   ex]
  (let [death-handler (u/require-resolve death-handler-fn-sym)
        dead-at (or last-retried-at (u/epoch-time-ms))
        job (assoc-in job [:state :dead-at] dead-at)]
    (u/log-on-exceptions (death-handler job ex))
    (when-not skip-dead-queue
      (r/enqueue-sorted-set redis-conn d/prefixed-dead-queue dead-at job))))

(defn wrap-failure
  [execute]
  (fn [{:keys [redis-conn] :as opts} job]
    (try
      (execute opts job)
      (catch Exception ex
        (let [failed-job (set-failed-config job ex)
              retry-count (get-in failed-job [:state :retry-count])
              max-retries (get-in failed-job [:retry-opts :max-retries])]
          (if (< retry-count max-retries)
            (retry-job redis-conn failed-job ex)
            (bury-job redis-conn failed-job ex)))))))
