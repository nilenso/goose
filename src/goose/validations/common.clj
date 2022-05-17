(ns goose.validations.common)


(defn- redis-url-invalid?
  "Valid URL patterns:
  #1: redis://username:password@hostname:0-65353
  #2: redis://hostname:0-65353"
  [url]
  (not
    (re-matches #"redis://.+:[0-9]{1,5}" url)))

(defn- redis-pool-opts-invalid?
  "Valid pool options:
  #1: :none
  #2: {}
  #3: Satisfies taoensso.carmine.connections/IConnectionPool"
  [redis-pool-opts]
  (not
    (or
      (= :none redis-pool-opts)
      (= clojure.lang.PersistentArrayMap (type redis-pool-opts))
      (satisfies? taoensso.carmine.connections/IConnectionPool redis-pool-opts))))

(defn validate-redis
  [opts]
  (when-let
    [validation-error
     (cond
       (let [redis-url (get-in opts [:redis-conn :spec :uri])]
         (common/redis-url-invalid? redis-url)
         ["Invalid redis URL" (u/wrap-error :invalid-redis-url redis-url)])

       (let [redis-pool-opts (get-in opts [:redis-conn :pool])]
         (common/redis-pool-opts-invalid? redis-pool-opts)
         ["Invalid redis pool opts" (u/wrap-error :invalid-redis-pool-opts redis-pool-opts)]))]
    (throw (apply ex-info validation-error))))
