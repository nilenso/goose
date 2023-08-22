(ns goose.specs
  (:require
    [goose.broker :as b]
    [goose.client :as c]
    [goose.cron.parsing :as cron-parsing]
    [goose.defaults :as d]
    [goose.metrics :as m]
    [goose.utils :as u]

    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [taoensso.carmine.connections :refer [IConnectionPool]])
  (:import
    (java.time Instant ZoneId)))

;;; ========== Qualified Function Symbols ==============
(s/def ::fn-sym (s/and qualified-symbol? resolve #(fn? @(resolve %))))

;;; ========== Redis ==============
(s/def :goose.specs.redis/url string?)
(s/def :goose.specs.redis/pool-opts
  (s/or :nil nil?
        :none #{:none}
        :map map?
        :iconn-pool #(satisfies? IConnectionPool %)))

(s/def ::redis-conn-opts
  (s/keys :req-un [:goose.specs.redis/url]
          :opt-un [:goose.specs.redis/pool-opts]))
(s/def ::redis-scheduler-polling-interval-sec (s/int-in 1 61))

;;; ========== RabbitMQ ==============
(s/def :goose.specs.rmq/uri string?)
(s/def :goose.specs.rmq/host string?)
(s/def :goose.specs.rmq/port int?)
(s/def :goose.specs.rmq/username string?)
(s/def :goose.specs.rmq/password string?)
(s/def :goose.specs.rmq/vhost string?)
;;; A non-exhaustive list of RabbitMQ settings.
;;; Full list of settings can be found here:
;;; http://clojurerabbitmq.info/articles/connecting.html
(s/def :goose.specs.rmq/settings
  (s/keys :opt-un [:goose.specs.rmq/uri
                   :goose.specs.rmq/host
                   :goose.specs.rmq/port
                   :goose.specs.rmq/username
                   :goose.specs.rmq/password
                   :goose.specs.rmq/vhost]))

(s/def :goose.specs.sync/strategy #{d/sync-confirms})
(s/def ::timeout-ms pos-int?)
(s/def ::retry-delay-ms pos-int?)
(s/def ::sync-strategy
  (s/keys :req-un [:goose.specs.sync/strategy ::timeout-ms]
          :opt-un [::max-retries ::retry-delay-ms]))

(s/def :goose.specs.async/strategy #{d/async-confirms})
(s/def ::ack-handler fn?)
(s/def ::nack-handler fn?)
(s/def ::async-strategy
  (s/keys :req-un [:goose.specs.async/strategy ::ack-handler ::nack-handler]))

(s/def :goose.specs.classic/type #{d/rmq-classic-queue})
(s/def ::classic-queue
  (s/keys :req-un [:goose.specs.classic/type]))

(s/def :goose.specs.quorum/type #{d/rmq-quorum-queue})
(s/def ::replication-factor pos-int?)
(s/def ::quorum-queue
  (s/keys :req-un [:goose.specs.quorum/type ::replication-factor]))

(s/def :goose.specs.rmq/queue-type
  (s/or :classic ::classic-queue
        :quorum ::quorum-queue))

(s/def ::publisher-confirms
  (s/or :sync ::sync-strategy
        :async ::async-strategy))

(s/def ::return-listener fn?)
(s/def ::shutdown-listener fn?)

(s/def ::rmq
  (s/keys :req-un [:goose.specs.rmq/settings
                   :goose.specs.rmq/queue-type
                   ::publisher-confirms
                   ::return-listener
                   ::shutdown-listener]))

;;; ============== Brokers ==============
(s/def ::broker #(satisfies? b/Broker %))

;;; ============== Queue ==============
(defn- unprefixed? [queue] (not (str/starts-with? queue d/queue-prefix)))
(defn- not-protected? [queue] (not (str/includes? d/protected-queues queue)))
;;; RMQ queue names cannot be longer than 255 bytes.
(s/def ::queue (s/and string? #(< (count %) 200) unprefixed? not-protected?))

;;; ============== Cron Opts ==============
(s/def ::cron-name string?)
(s/def ::cron-schedule (s/and string? cron-parsing/valid-cron?))
(s/def ::timezone (set (ZoneId/getAvailableZoneIds)))
(s/def ::cron-opts
  (s/keys :req-un [::cron-name
                   ::cron-schedule]
          :opt-un [::timezone]))

;;; ============== Batch ==============
(s/def ::callback-fn-sym (s/nilable ::fn-sym))
(s/def ::linger-in-hours nat-int?)
(s/def ::batch-opts
  (s/keys :req-un [::linger-in-hours]
          :opt-un [::callback-fn-sym]))
(s/def ::batch-args (s/and sequential? #(every? sequential? %) (s/* ::args-serializable?)))

;;; ============== Retry Opts ==============
(s/def ::max-retries nat-int?)
(s/def ::retry-queue (s/nilable ::queue))
(s/def ::retry-delay-sec-fn-sym
  (s/and ::fn-sym #(some #{1} (u/arities %))))
(s/def ::error-handler-fn-sym
  (s/and ::fn-sym #(some #{3} (u/arities %))))
(s/def ::death-handler-fn-sym
  (s/and ::fn-sym #(some #{3} (u/arities %))))
(s/def ::skip-dead-queue boolean?)
(s/def ::retry-opts
  (s/and
    (s/map-of #{:max-retries
                :retry-delay-sec-fn-sym
                :retry-queue
                :error-handler-fn-sym
                :death-handler-fn-sym
                :skip-dead-queue}
              any?)
    (s/keys :req-un [::max-retries
                     ::retry-delay-sec-fn-sym
                     ::error-handler-fn-sym
                     ::death-handler-fn-sym
                     ::skip-dead-queue]
            :opt-un [::retry-queue])))

;;; ============== StatsD Metrics ==============
(s/def :goose.specs.statsd/enabled? boolean?)
(s/def :goose.specs.statsd/host string?)
(s/def :goose.specs.statsd/port pos-int?)
(s/def :goose.specs.statsd/prefix string?)
(s/def :goose.specs.statsd/sample-rate double?)
(s/def :goose.specs.statsd/tags map?)
(s/def ::statsd-opts
  (s/keys :req-un [:goose.specs.statsd/enabled?]
          :opt-un [:goose.specs.statsd/host
                   :goose.specs.statsd/port
                   :goose.specs.statsd/prefix
                   :goose.specs.statsd/tags
                   :goose.specs.statsd/sample-rate]))

;;; ============== Client ==============
(s/def ::args-serializable?
  #(try (= % (u/decode (u/encode %)))
        (catch Exception _ false)))
(s/def ::instant #(instance? Instant %))
(s/def ::client-opts (s/keys :req-un [::broker ::queue ::retry-opts]))

;;; ============== Worker ==============
(s/def ::threads pos-int?)
(s/def ::graceful-shutdown-sec pos-int?)
(s/def ::metrics-plugin #(satisfies? m/Metrics %))
(s/def ::middlewares fn?)
(s/def ::error-service-config any?) ; This varies by error services.
(s/def ::worker-opts
  (s/keys :req-un [::broker
                   ::threads
                   ::queue
                   ::graceful-shutdown-sec]
          :opt-un [::middlewares
                   ::error-service-config
                   ::metrics-plugin]))

;;; ============== FDEFs ==============
(s/fdef c/perform-async
        :args (s/cat :opts ::client-opts
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?))
        :ret map?)

(s/fdef c/perform-at
        :args (s/cat :opts ::client-opts
                     :instant ::instant
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?))
        :ret map?)

(s/fdef c/perform-in-sec
        :args (s/cat :opts ::client-opts
                     :sec int?
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?))
        :ret map?)

(s/fdef c/perform-every
        :args (s/cat :opts ::client-opts
                     :cron-opts ::cron-opts
                     :execute-fn-sym ::fn-sym
                     :args (s/* ::args-serializable?))
        :ret map?)

(s/fdef c/perform-batch
        :args (s/cat :opts ::client-opts
                     :batch-opts ::batch-opts
                     :execute-fn-sym ::fn-sym
                     :args ::batch-args)
        :ret map?)

(def ^:private fns-with-specs
  [`c/perform-async
   `c/perform-at
   `c/perform-in-sec
   `c/perform-every
   `c/perform-batch])

(defn instrument
  "Instruments frequently-called functions.\\
  By default, Instrumentation is disabled.\\
  It is recommended to enable Specs in Development & Staging.\\
  Disabling Specs in Production has a 40% performance improvement.\\
  Only disable Specs in Production after thorough testing in Staging."
  []
  (st/instrument fns-with-specs))

(defn unstrument
  "Disables instrumentation of frequently-called functions."
  []
  (st/unstrument fns-with-specs))

(defn- assert-specs
  [ns-fn spec x]
  (when-let [fail (s/explain-data spec x)]
    (throw (ex-info (format "Call to %s did not conform to spec." ns-fn) fail))))

;;; Single-use functions have manual assertions.
;;; (s/fdef) is declared redundantly for
;;; the purpose of `clojure.repl/doc`.

(s/fdef goose.brokers.redis.broker/new-producer
        :args (s/cat :redis ::redis-conn-opts)
        :ret ::broker)
(defn ^:no-doc assert-redis-producer [conn-opts]
  (assert-specs 'goose.brokers.redis.broker/new-producer ::redis-conn-opts conn-opts))

(s/fdef goose.brokers.redis.broker/new-consumer
        :args (s/alt :one (s/cat :redis ::redis-conn-opts)
                     :two (s/cat :redis ::redis-conn-opts
                                 :scheduler-polling-interval-sec ::redis-scheduler-polling-interval-sec))
        :ret ::broker)
(defn ^:no-doc assert-redis-consumer
  [conn-opts scheduler-polling-interval-sec]
  (assert-specs 'goose.brokers.redis.broker/new-consumer ::redis-conn-opts conn-opts)
  (assert-specs 'goose.brokers.redis.broker/new-consumer
                ::redis-scheduler-polling-interval-sec
                scheduler-polling-interval-sec))

(s/fdef goose.brokers.rmq.broker/new-producer
        :args (s/alt :one (s/cat :opts ::rmq)
                     :two (s/cat :opts ::rmq
                                 :channels pos-int?))
        :ret ::broker)
(defn ^:no-doc assert-rmq-producer
  [opts channels]
  (assert-specs 'goose.brokers.rmq.broker/new-producer ::rmq opts)
  (assert-specs 'goose.brokers.rmq.broker/new-producer pos-int? channels))

(s/fdef goose.brokers.rmq.broker/new-consumer
        :args (s/cat :opts ::rmq)
        :ret ::broker)
(defn ^:no-doc assert-rmq-consumer [opts]
  (assert-specs 'goose.brokers.rmq.broker/new-consumer ::rmq opts))

(s/fdef goose.metrics.statsd/new
        :args (s/cat :opts ::statsd-opts)
        :ret ::metrics-plugin)
(defn ^:no-doc assert-statsd [opts]
  (assert-specs 'goose.metrics.statsd/new ::statsd-opts opts))

(s/fdef goose.worker/start
        :args (s/cat :opts ::worker-opts))
(defn ^:no-doc assert-worker [opts]
  (assert-specs 'goose.worker/start ::worker-opts opts))
