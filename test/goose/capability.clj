(ns goose.capability
  (:require
   [clojure.set :refer [intersection difference]])
  (:import [java.lang.reflect Modifier]))

(defn- extract-methods [class]
  (mapv first
        (remove #(Modifier/isAbstract (second %))
                (mapv (juxt #(.getName %) #(.getModifiers %))
                      (.getMethods class)))))

;; capabilities of the broker protocol
(def cap-set (set (mapv #(.getName %)
                        (.getMethods goose.broker.Broker))))

(defn- evaluate-broker [implementation]
  (let [all-methods (set (extract-methods implementation))
        implemented (intersection cap-set all-methods)
        lacking     (difference cap-set all-methods)]
    {:Broker (.getSimpleName implementation)
     :implemented    (vec implemented)
     :lacking        (vec lacking)
     :coverage       (str (format "%.2f"  (* 100
                                             (float (/ (count implemented)
                                                       (count cap-set)))))
                          " %")}))

(defn gauge [opts]
  "evaluates capabilities of broker implementations"
  (let [implementations [goose.brokers.redis.broker.Redis
                         goose.brokers.rmq.broker.RabbitMQ]]
    (try
      (mapv evaluate-broker implementations)
      (catch Exception e
        (println "Error evaluating broker capabilities:" (.getMessage e))))))

(comment
  (type (first (.getMethods goose.brokers.redis.broker.Redis)))
  ;; java.lang.reflect.Method
  ;; https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Method.html
  ;; this exposes a getModifiers method
  ;; rely on the class modifier having the abstract enum : https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Modifier.html
  ;; jump into this java.lang.reflect.Modifier
  ;; this also https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
  ;; (the Class Class)
  (.getModifiers
   (first (.getMethods goose.brokers.redis.broker.Redis)))

  (extract-methods  goose.brokers.redis.broker.Redis)

  (extract-methods goose.brokers.rmq.broker.RabbitMQ)

  (evaluate-broker goose.brokers.redis.broker.Redis)

  (evaluate-broker goose.brokers.rmq.broker.RabbitMQ)

  (gauge []))
