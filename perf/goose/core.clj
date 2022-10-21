(ns goose.core
  (:require
    [goose.metrics.statsd :as statsd]
    [goose.utils :as u]
    [goose.worker :as w]

    [criterium.core :as criterium]))

(def queue "load-test")
(defn my-fn [arg]
  (when (= 0 (mod arg 100))
    (throw (ex-info "1% jobs fail" {}))))

(defn latency [enqueue-time]
  (println "Latency:" (- (u/epoch-time-ms) enqueue-time) "ms"))

;;; ========== Retry Opts ==========
(defn dummy-error-handler [_ _ _])
(defn retry-delay-sec [_] 1)
(def retry-opts {:max-retries            1
                 :skip-dead-queue        true
                 :retry-delay-sec-fn-sym `retry-delay-sec
                 :error-handler-fn-sym   `dummy-error-handler
                 :death-handler-fn-sym   `dummy-error-handler})

;;; ========== Client Opts ==========
(def client-opts
  {:queue      queue
   :retry-opts retry-opts})

;;; ========== Worker Opts ==========
(def worker-opts (assoc w/default-opts
                   :queue queue
                   :threads 25
                   :metrics-plugin (statsd/new {:enabled? false})))


;;; ========== Toxiproxy ==========
(defn run-benchmarks
  [toxiproxy-fn enqueue-dequeue-fn count]
  (println "====================================
Benchmark runs beyond 60 minutes.
We're using criterium to warm-up JVM & pre-run GC.
Exit using ctrl-C after recording ~10 samples.
====================================")
  (let [proxy (toxiproxy-fn)]
    (try
      ;; A shutdown hook helps reset toxiproxy when ctrl-C is pressed.
      (.addShutdownHook
        (Runtime/getRuntime)
        (Thread. #(.delete proxy)))
      (criterium/bench (enqueue-dequeue-fn count))
      (finally (.delete proxy)))))
