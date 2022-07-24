(ns goose.specs-test
  (:require
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.specs :as specs]
    [goose.test-utils :as tu]
    [goose.worker :as w]

    [clojure.test :refer [deftest is are]]))

(defn single-arity-fn [_] "dummy")
(def now (java.time.Instant/now))

; Pending specs/unit tests:
; - retry-opts don't contain extra keys
(deftest specs-test
  (specs/instrument)
  (are [sut]
    (is
      (thrown?
        clojure.lang.ExceptionInfo
        (sut)))

    ; :execute-fn-sym
    #(c/perform-async tu/client-opts 'my-fn)
    #(c/perform-async tu/client-opts `my-fn)
    #(c/perform-async tu/client-opts `tu/client-opts)

    ; :args
    #(c/perform-async tu/client-opts `tu/my-fn specs-test)

    ; :sec
    #(c/perform-in-sec tu/client-opts 0.2 `tu/my-fn)

    ; :date-time
    #(c/perform-at tu/client-opts "22-July-2022" `tu/my-fn)

    ; Worker specs
    #(w/start (assoc tu/worker-opts :threads -1.1))
    #(w/start (assoc tu/worker-opts :graceful-shutdown-sec -2))
    #(w/start (assoc tu/worker-opts :scheduler-polling-interval-sec 0))

    ; :statad-opts
    #(w/start (assoc-in tu/worker-opts [:statsd-opts :enabled?] 1))
    #(w/start (assoc-in tu/worker-opts [:statsd-opts :host] 127.0))
    #(w/start (assoc-in tu/worker-opts [:statsd-opts :port] "8125"))
    #(w/start (assoc-in tu/worker-opts [:statsd-opts :sample-rate] 1))
    #(w/start (assoc-in tu/worker-opts [:statsd-opts :tags] '("service:maverick")))

    ; Common specs
    ; :queue
    #(c/perform-async (assoc tu/client-opts :queue :non-string) `tu/my-fn)
    #(w/start (assoc tu/worker-opts :queue (str (range 300))))
    #(c/perform-at (assoc tu/client-opts :queue d/schedule-queue) now `tu/my-fn)
    #(c/perform-in-sec (assoc tu/client-opts :queue d/dead-queue) 1 `tu/my-fn)
    #(c/perform-async (assoc tu/client-opts :queue (str d/queue-prefix "olttwa")) `tu/my-fn)

    ; :retry-opts
    #(c/perform-async (assoc-in tu/client-opts [:retry-opts :max-retries] -1) `tu/my-fn)
    #(c/perform-at (assoc-in tu/client-opts [:retry-opts :retry-queue] :invalid) now `tu/my-fn)
    #(c/perform-in-sec (assoc-in tu/client-opts [:retry-opts :error-handler-fn-sym] `single-arity-fn) 1 `tu/my-fn)
    #(c/perform-async (assoc-in tu/client-opts [:retry-opts :death-handler-fn-sym] `single-arity-fn) `tu/my-fn)
    #(c/perform-at (assoc-in tu/client-opts [:retry-opts :retry-delay-sec-fn-sym] 'single-arity-fn) now `tu/my-fn)
    #(c/perform-in-sec (assoc-in tu/client-opts [:retry-opts :skip-dead-queue] 1) 1 `tu/my-fn)

    ; :broker-opts
    #(w/start (assoc-in tu/worker-opts [:broker-opts :rmq] "2-brokers"))

    ; :redis-opts
    #(c/perform-async (assoc-in tu/client-opts [:broker-opts :redis :url] "invalid-url") `tu/my-fn)
    #(w/start (assoc-in tu/worker-opts [:broker-opts :redis :pool-opts] "invalid-pool-opts"))))
