(ns goose.capability
  (:require
   [clojure.string :as s]
   [clojure.set :refer [intersection difference]])
  (:import [java.lang.reflect Modifier]))

(defn- extract-methods [class]
  (mapv first
        (remove #(Modifier/isAbstract (second %))
                (mapv (juxt #(.getName %) #(.getModifiers %))
                      (.getMethods class)))))

;; capabilities of the broker protocol
(def potential (set (mapv #(.getName %)
                          (.getMethods goose.broker.Broker))))

(def implementations
  [goose.brokers.redis.broker.Redis
   goose.brokers.rmq.broker.RabbitMQ])

;; granularity of capabilities being presented from a user perspective : refer comments in the protocol
;; testing utitities can continue with the functionality guarantees
(defn- evaluate-broker [implementation]
  (let [all-methods (set (extract-methods implementation))
        implemented (intersection potential all-methods)
        lacking     (difference potential all-methods)]
    {:broker (s/lower-case (.getSimpleName implementation))
     :implemented    (vec implemented)
     :lacking        (vec lacking)
     :coverage       (str (format "%.2f"  (* 100
                                             (float (/ (count implemented)
                                                       (count potential)))))
                          " %")}))

(defn- gauge
  "evaluates capabilities of broker implementations"
  []
  (try
    (mapv evaluate-broker implementations)
    (catch Exception e
      (println "Error evaluating broker capabilities:" (.getMessage e)))))

(def capabilities (gauge))

(def fetch-capabilities
  (memoize
   (fn [broker]
     (->> capabilities
          (filter #(= (:broker %) broker))
          (first)
          (:implemented)
          (set)))))

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

  (gauge))
