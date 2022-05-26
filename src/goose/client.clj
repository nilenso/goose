(ns goose.client
  (:require
    [goose.config :as cfg]
    [goose.redis :as r]
    [goose.validations.client :refer [validate-async-params]]))

(defn- job-id []
  (str (random-uuid)))

(defn- epoch-time []
  (quot (System/currentTimeMillis) 1000))

(defn- schedule-time
  [{:keys [perform-at perform-in-sec]}]
  (cond
    perform-at
    (.getTime perform-at)

    perform-in-sec
    (+ (* 1000 perform-in-sec) (.getTime (java.util.Date.)))))

(defn- route-job
  [queue schedule]
  (let [delay (schedule-time schedule)]
    (cond
      delay
      [(str cfg/queue-prefix cfg/schedule-queue) delay]

      :else
      [(str cfg/queue-prefix queue)])))

(defn- push-job
  ([conn job queue]
   (r/enqueue-back conn queue job))
  ([conn job queue time]
   (if (< time (epoch-time))
     (r/enqueue-front conn queue job)
     (r/enqueue-sorted-set conn queue time job))))

(def schedule-opts
  "perform-at & perform-in-sec opts are mutually exclusive."
  {:perform-at     nil
   :perform-in-sec nil})

(def default-opts
  {:redis-url       cfg/default-redis-url
   :redis-pool-opts {}
   :queue           cfg/default-queue
   :schedule        schedule-opts
   :retries         0})

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  Usage:
  - (async `foo)
  - (async `foo {:args '(:bar) :retries 2})
  Validations:
  - A function must be a resolvable & a fully qualified symbol
  - Args must be edn-serializable
  - Retries must be non-negative
  edn: https://github.com/edn-format/edn"
  [{:keys [redis-url redis-pool-opts
           queue schedule retries]}
   resolvable-fn-symbol
   & args]
  (validate-async-params
    redis-url redis-pool-opts
    queue schedule retries
    resolvable-fn-symbol args)
  (let [redis-conn (r/conn redis-url redis-pool-opts)
        job {:id          (job-id)
             :queue       queue
             :fn-sym      resolvable-fn-symbol
             :args        args
             :retries     retries
             :enqueued-at (epoch-time)}
        queue-opts (route-job queue schedule)
        push-job-params (concat [redis-conn job] queue-opts)]
    (apply push-job push-job-params)
    (:id job)))
