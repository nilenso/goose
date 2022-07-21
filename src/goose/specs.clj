(ns goose.specs
  (:require
    [goose.client :as client]
    [goose.defaults :as d]

    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as string]
    [taoensso.nippy :as nippy]
    [taoensso.carmine.connections :refer [IConnectionPool]]))

; Valid Redis URL patterns:
; #1: redis://username:password@hostname:0-65353
; #2: redis://hostname:0-65353
(s/def :redis/url #(re-matches #"redis://.+:[0-9]{1,5}" %))
(s/def :redis/pool-opts
  (s/or #(= :none %)
        #(map? %)
        #(satisfies? IConnectionPool %)))

(s/def :broker/redis
  (s/keys :req-un [:redis/url]
          :opt-un [:redis/pool-opts]))
(s/def ::broker-opts
  (s/or :redis (s/keys :req-un [:broker/redis])))

(s/def ::queue
  (s/and string?
         #(< (count %) 1000)
         #(not (string/starts-with? % d/queue-prefix))
         #(not (string/includes? d/protected-queues %))))

(s/def ::retry-opts any?)
(s/def ::client/opts (s/keys :req-un [::broker-opts ::queue ::retry-opts]))

(s/def ::fn? #(fn? @(resolve %)))
(s/def ::fn-sym (s/and qualified-symbol? resolve ::fn?))

(defn- serializable? [arg]
  (try (= arg (nippy/thaw (nippy/freeze arg)))
       (catch Exception _ false)))
(s/def :args/serializable? serializable?)

(s/fdef client/perform-async
        :args (s/cat :opts ::client/opts
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(def ^:private fns-with-specs
  [`client/perform-async])

(defn instrument []
  (st/instrument fns-with-specs))

(defn unstrument []
  (st/unstrument fns-with-specs))
