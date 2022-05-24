(ns goose.validations.redis
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
      (map? redis-pool-opts)
      (satisfies? IConnectionPool redis-pool-opts))))

(defn validate-redis
  [redis-url redis-pool-opts]
  (when-let
    [validation-error
     (cond
       (redis-url-invalid? redis-url)
       ["Invalid redis URL" (u/wrap-error :invalid-redis-url redis-url)]

       (redis-pool-opts-invalid? redis-pool-opts)
       ["Invalid redis pool opts" (u/wrap-error :invalid-redis-pool-opts redis-pool-opts)])]
    (throw (apply ex-info validation-error))))
