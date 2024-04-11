(ns goose.brokers.redis.console.specs
  (:require [clojure.spec.alpha :as s])
  (:import
    (java.lang Long)))

(s/def ::page (s/and pos-int?))

(defn str->long
  [str]
  (if (= (type str) Long)
    str
    (try (Long/parseLong str)
         (catch Exception _ :clojure.spec.alpha/invalid))))
