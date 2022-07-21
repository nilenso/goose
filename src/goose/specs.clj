(ns goose.specs
  (:require
    [goose.client :as client]
    [goose.defaults :as d]
    [goose.utils :as u]

    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as string]
    [taoensso.nippy :as nippy]
    [taoensso.carmine.connections :refer [IConnectionPool]]))

; ========== Redis ==============
; Valid Redis URL patterns:
; #1: redis://username:password@hostname:0-65353
; #2: redis://hostname:0-65353
(s/def :redis/url #(re-matches #"redis://.+:[0-9]{1,5}" %))
(s/def :redis/pool-opts
  (s/or :none #(= :none %)
        :map #(map? %)
        :iconn-pool #(satisfies? IConnectionPool %)))

; ============== Brokers ==============
(s/def :broker/redis
  (s/keys :req-un [:redis/url]
          :opt-un [:redis/pool-opts]))
(s/def ::broker-opts
  (s/or :redis (s/keys :req-un [:broker/redis])))

; ============== Queue ==============
(s/def ::queue
  (s/and string?
         #(< (count %) 1000)
         #(not (string/starts-with? % d/queue-prefix))
         #(not (string/includes? d/protected-queues %))))

; ============== Retry Opts ==============
(s/def :retry/max-retries nat-int?)
(s/def :retry/retry-delay-sec-fn-sym
  (s/and ::fn-sym
         #(pos-int? (@(resolve %) 0))))
(s/def :retry/retry-queue (s/nilable ::queue))
(s/def :retry/handler-fn-sym
  (s/and ::fn-sym
         #(some #{2} (u/arities %))))
(s/def :retry/error-handler-fn-sym :retry/handler-fn-sym)
(s/def :retry/death-handler-fn-sym :retry/handler-fn-sym)
(s/def :retry/skip-dead-queue boolean?)

(s/def ::retry-opts (s/keys :req-un [:retry/max-retries :retry/retry-delay-sec-fn-sym
                                     :retry/error-handler-fn-sym :retry/skip-dead-queue
                                     :retry/death-handler-fn-sym]
                            :opt-un [:retry/retry-queue]))

; ============== Client ==============
(s/def ::client/opts (s/keys :req-un [::broker-opts ::queue]
                             :opt-un [::retry-opts]))

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

(s/fdef client/perform-at
        :args (s/cat :opts ::client/opts
                     :date-time inst?
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(s/fdef client/perform-in-sec
        :args (s/cat :opts ::client/opts
                     :sec int?
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(def ^:private fns-with-specs
  [`client/perform-async])

(defn instrument []
  (st/instrument fns-with-specs))

(defn unstrument []
  (st/unstrument fns-with-specs))
