(ns goose.capability
  (:require
   [clojure.string :as s]
   [clojure.set :refer [intersection difference]])
  (:import [java.lang.reflect Modifier]))

(def implementations
  [goose.brokers.redis.broker.Redis
   goose.brokers.rmq.broker.RabbitMQ])

(defn- extract-methods [class]
  (->> class
       (.getMethods)
       (remove #(Modifier/isAbstract (.getModifiers %)))
       (mapv #(.getName %))))

(def potential (set (mapv #(.getName %)
                          (.getMethods goose.broker.Broker))))

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

(defn- gauge []
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
