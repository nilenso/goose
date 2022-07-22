(ns goose.specs
  (:require
    [goose.client :as c]
    [goose.defaults :as d]
    [goose.utils :as u]
    [goose.worker :as w]

    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as string]
    [taoensso.nippy :as nippy]
    [taoensso.carmine.connections :refer [IConnectionPool]]))

; ========== Qualified Function Symbols ==============
(defn- resolvable-fn? [func] (fn? @(resolve func)))
(s/def ::fn-sym (s/and qualified-symbol? resolve resolvable-fn?))

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
(defn- unprefixed? [queue] (not (string/starts-with? queue d/queue-prefix)))
(defn- not-protected? [queue] (not (string/includes? d/protected-queues queue)))
(defn- len-below-1000? [queue] (< (count queue) 1000))
(s/def ::queue
  (s/and string? len-below-1000? unprefixed? not-protected?))

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
(s/def ::retry-opts
  (s/keys :req-un [:retry/max-retries :retry/retry-delay-sec-fn-sym :retry/skip-dead-queue
                   :retry/error-handler-fn-sym :retry/death-handler-fn-sym]
          :opt-un [:retry/retry-queue]))

; ============== Statsd Opts ==============
(s/def :statsd/enabled? boolean?)
(s/def :statsd/host string?)
(s/def :statsd/port pos-int?)
(s/def :statsd/sample-rate double?)
(s/def :statsd/tags map?)
(s/def ::statsd-opts
  (s/keys :req-un [:statsd/enabled?]
          :opt-un [:statsd/host :statsd/port
                   :statsd/sample-rate :statsd/tags]))

; ============== Client ==============
(defn- serializable? [arg]
  (try (= arg (nippy/thaw (nippy/freeze arg)))
       (catch Exception _ false)))
(s/def :args/serializable? serializable?)
(s/def ::c/opts (s/keys :req-un [::broker-opts ::queue]
                        :opt-un [::retry-opts]))

; ============== Worker ==============
(s/def ::threads pos-int?)
(s/def ::graceful-shutdown-sec pos-int?)
(s/def ::scheduler-polling-interval-sec pos-int?)
(s/def ::w/opts (s/keys :req-un [::broker-opts ::queue ::threads
                                 ::scheduler-polling-interval-sec
                                 ::graceful-shutdown-sec ::statsd-opts]))

; ============== FDEFs ==============
(s/fdef c/perform-async
        :args (s/cat :opts ::c/opts
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(s/fdef c/perform-at
        :args (s/cat :opts ::c/opts
                     :date-time inst?
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(s/fdef c/perform-in-sec
        :args (s/cat :opts ::c/opts
                     :sec int?
                     :execute-fn-sym ::fn-sym
                     :args (s/* :args/serializable?)))

(s/fdef w/start
        :args (s/cat :opts ::w/opts))

(def ^:private fns-with-specs
  [`c/perform-async
   `c/perform-at
   `c/perform-in-sec
   `w/start])

(defn instrument []
  (st/instrument fns-with-specs))

(defn unstrument []
  (st/unstrument fns-with-specs))
