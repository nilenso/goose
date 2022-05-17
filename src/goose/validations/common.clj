(ns goose.validations.common
  (:require
    [goose.utils :as u]
    [taoensso.carmine.connections :refer [IConnectionPool]]))

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
      (satisfies? IConnectionPool redis-pool-opts))))

(defn validate-redis
  [opts]
  (when-let
    [validation-error
     (cond
       (redis-url-invalid? (get-in opts [:redis-conn :spec :uri]))
       ["Invalid redis URL" (u/wrap-error :invalid-redis-url (get-in opts [:redis-conn :spec :uri]))]

       (redis-pool-opts-invalid? (get-in opts [:redis-conn :pool]))
       ["Invalid redis pool opts" (u/wrap-error :invalid-redis-pool-opts (get-in opts [:redis-conn :pool]))])]
    (throw (apply ex-info validation-error))))
