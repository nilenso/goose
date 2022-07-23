(ns goose.specs-test
  (:require
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.test-utils :as tu]
    [goose.specs :as specs]

    [clojure.test :refer [deftest is are]]
    [goose.worker :as w]))

(defn catch-ex
  [func]
  (try
    (func)
    (catch Exception ex
      [(tu/spec-path ex) (tu/spec-problem ex)])))

(deftest specs-test
  (specs/instrument)
  (are [input-path input-problem sut]
    (let [[path problem] (catch-ex sut)]
      (is (= input-path path))
      (if input-problem
        (is (= input-problem problem))
        true))

    ; Client specs
    [:execute-fn-sym] `qualified-symbol?
    #(c/perform-async tu/client-opts 'my-fn)

    [:execute-fn-sym] `resolve
    #(c/perform-async tu/client-opts `my-fn)

    [:execute-fn-sym] nil
    #(c/perform-async tu/client-opts `tu/client-opts)

    [:args] nil
    #(c/perform-async tu/client-opts `tu/my-fn specs-test)

    [:sec] `int?
    #(c/perform-in-sec tu/client-opts 0.2 `tu/my-fn)

    [:date-time] `inst?
    #(c/perform-at tu/client-opts "22-July-2022" `tu/my-fn)

    ; Worker specs
    [:opts :threads] `pos-int?
    #(w/start (assoc tu/worker-opts :threads -1.1))

    [:opts :graceful-shutdown-sec] `pos-int?
    #(w/start (assoc tu/worker-opts :graceful-shutdown-sec -2))

    [:opts :scheduler-polling-interval-sec] `pos-int?
    #(w/start (assoc tu/worker-opts :scheduler-polling-interval-sec 0))

    ; Common specs
    ; ==> Queue specs
    [:opts :queue] `string?
    #(c/perform-async (assoc tu/client-opts :queue :non-string) `tu/my-fn)

    [:opts :queue] nil
    #(w/start (assoc tu/worker-opts :queue (str (range 300))))

    [:opts :queue] 'goose.specs/not-protected?
    #(c/perform-at (assoc tu/client-opts :queue d/schedule-queue) (java.time.Instant/now) `tu/my-fn)

    [:opts :queue] 'goose.specs/not-protected?
    #(c/perform-in-sec (assoc tu/client-opts :queue d/dead-queue) 1 `tu/my-fn)

    [:opts :queue] 'goose.specs/unprefixed?
    #(c/perform-async (assoc tu/client-opts :queue (str d/queue-prefix "olttwa")) `tu/my-fn)

    ; ==> Broker specs
    [:opts :broker-opts] nil
    #(w/start (assoc-in tu/worker-opts [:broker-opts :rmq] "2-brokers"))

    ; ==> Redis specs
    [:opts :broker-opts :redis :redis :url] nil
    #(c/perform-async (assoc-in tu/client-opts [:broker-opts :redis :url] "invalid-url") `tu/my-fn)

    [:opts :broker-opts :redis :redis :pool-opts :none] nil
    #(w/start (assoc-in tu/worker-opts [:broker-opts :redis :pool-opts] "invalid-pool-opts"))

    ))
