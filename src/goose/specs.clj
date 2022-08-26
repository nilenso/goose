(ns goose.specs
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.broker :as redis]
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.metrics.protocol :as metrics-protocol]
    [goose.metrics.statsd :as statsd]
    [goose.utils :as u]
    [goose.worker :as w]

    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as string]
    [taoensso.carmine.connections :refer [IConnectionPool]]
    [taoensso.nippy :as nippy]))

; ========== Qualified Function Symbols ==============
(s/def ::fn-sym (s/and qualified-symbol? resolve #(fn? @(resolve %))))

; ========== Redis ==============
(s/def :goose.specs.redis/url string?)
(s/def :goose.specs.redis/scheduler-polling-interval-sec pos-int?)
(s/def :goose.specs.redis/pool-opts
  (s/or :none #(= :none %)
        :map map?
        :iconn-pool #(satisfies? IConnectionPool %)))

(s/def ::redis
  (s/keys :req-un [:goose.specs.redis/url
                   :goose.specs.redis/scheduler-polling-interval-sec]
          :opt-un [:goose.specs.redis/pool-opts]))
(s/fdef redis/new
        :args (s/alt :one (s/cat :redis ::redis)
                     :two (s/cat :redis ::redis
                                 :thread-count (s/or :nil nil? :int pos-int?))))
; ============== Brokers ==============
(s/def ::broker #(satisfies? b/Broker %))

; ============== Queue ==============
(defn- unprefixed? [queue] (not (string/starts-with? queue d/queue-prefix)))
(defn- not-protected? [queue] (not (string/includes? d/protected-queues queue)))
(s/def ::queue (s/and string? #(< (count %) 1000) unprefixed? not-protected?))

; ============== Retry Opts ==============
(s/def ::max-retries nat-int?)
(s/def ::retry-queue (s/nilable ::queue))
(s/def ::skip-dead-queue boolean?)
(s/def ::retry-delay-sec-fn-sym
  (s/and ::fn-sym #(some #{1} (u/arities %))))
(s/def ::error-handler-fn-sym
  (s/and ::fn-sym #(some #{3} (u/arities %))))
(s/def ::death-handler-fn-sym
  (s/and ::fn-sym #(some #{3} (u/arities %))))
(s/def ::retry-opts
  (s/and
    (s/map-of #{:max-retries :retry-delay-sec-fn-sym :skip-dead-queue
                :retry-queue :error-handler-fn-sym :death-handler-fn-sym} any?)
    (s/keys :req-un [::max-retries ::retry-delay-sec-fn-sym ::skip-dead-queue
                     ::error-handler-fn-sym ::death-handler-fn-sym]
            :opt-un [::retry-queue])))

; ============== Metrics Opts ==============
(s/def :goose.specs.metrics/enabled? boolean?)
(s/def :goose.specs.metrics/host string?)
(s/def :goose.specs.metrics/port pos-int?)
(s/def :goose.specs.metrics/sample-rate double?)
(s/def :goose.specs.metrics/tags map?)
(s/def ::statsd-opts
  (s/keys :req-un [:goose.specs.metrics/enabled?]
          :opt-un [:goose.specs.metrics/host
                   :goose.specs.metrics/port
                   :goose.specs.metrics/tags
                   :goose.specs.metrics/sample-rate]))
(s/fdef statsd/new
        :args (s/cat :opts ::statsd-opts))

; ============== Client ==============
(s/def ::args-serializable?
  #(try (= % (nippy/thaw (nippy/freeze %)))
        (catch Exception _ false)))
(s/def ::client-opts (s/keys :req-un [::broker ::queue]
                             :opt-un [::retry-opts]))

; ============== Worker ==============
(s/def ::threads pos-int?)
(s/def ::graceful-shutdown-sec pos-int?)
(s/def ::metrics-plugin #(satisfies? metrics-protocol/Protocol %))
(s/def ::worker-opts (s/keys :req-un [::broker ::queue ::threads
                                      ::graceful-shutdown-sec ::metrics-plugin]))

; ============== FDEFs ==============
(s/fdef c/perform-async
        :args (s/cat :opts ::client-opts
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?)))

(s/fdef c/perform-at
        :args (s/cat :opts ::client-opts
                     :date-time inst?
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?)))

(s/fdef c/perform-in-sec
        :args (s/cat :opts ::client-opts
                     :sec int?
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?)))

(s/fdef w/start
        :args (s/cat :opts ::worker-opts))

(def ^:private fns-with-specs
  [`redis/new
   `statsd/new
   `c/perform-async
   `c/perform-at
   `c/perform-in-sec
   `w/start])

(defn instrument []
  (st/instrument fns-with-specs))

(defn unstrument []
  (st/unstrument fns-with-specs))
