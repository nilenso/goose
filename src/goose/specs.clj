(ns goose.specs
  (:require
    [goose.brokers.broker :as b]
    [goose.brokers.redis.broker :as redis]
    [goose.client :as c]
    [goose.defaults :as d]
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
        :args (s/cat :redis ::redis))

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

; ============== Statsd Opts ==============
(s/def :goose.specs.statsd/enabled? boolean?)
(s/def :goose.specs.statsd/host string?)
(s/def :goose.specs.statsd/port pos-int?)
(s/def :goose.specs.statsd/sample-rate double?)
(s/def :goose.specs.statsd/tags map?)
(s/def ::statsd-opts
  (s/keys :req-un [:goose.specs.statsd/enabled?]
          :opt-un [:goose.specs.statsd/host
                   :goose.specs.statsd/port
                   :goose.specs.statsd/tags
                   :goose.specs.statsd/sample-rate]))

; ============== Client ==============
(s/def ::args-serializable?
  #(try (= % (nippy/thaw (nippy/freeze %)))
        (catch Exception _ false)))
(s/def ::client-opts (s/keys :req-un [::broker ::queue]
                             :opt-un [::retry-opts]))

; ============== Worker ==============
(s/def ::threads pos-int?)
(s/def ::graceful-shutdown-sec pos-int?)
(s/def ::worker-opts (s/keys :req-un [::broker ::queue ::threads
                                      ::graceful-shutdown-sec ::statsd-opts]))

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
   `c/perform-async
   `c/perform-at
   `c/perform-in-sec
   `w/start])

(defn instrument []
  (st/instrument fns-with-specs))

(defn unstrument []
  (st/unstrument fns-with-specs))
