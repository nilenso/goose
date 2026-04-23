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

(def potential (set (mapv #(.getName %)
                          (.getMethods goose.broker.Broker))))

(def implementations
  [goose.brokers.redis.broker.Redis
   goose.brokers.rmq.broker.RabbitMQ])

(defn- evaluate-broker [implementation]
  (let [all-methods (set (extract-methods implementation))
        implemented (intersection potential all-methods)
        lacking     (difference potential all-methods)]
    {:broker (keyword (s/lower-case (.getSimpleName implementation)))
     :implemented    (set (mapv keyword implemented))
     :lacking        (set (mapv keyword lacking))
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
          (:implemented)))))
