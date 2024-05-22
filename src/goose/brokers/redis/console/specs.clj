(ns ^:no-doc goose.brokers.redis.console.specs
  (:require [clojure.spec.alpha :as s])
  (:import
    (java.lang Long)))

(s/def ::page (s/and pos-int?))
(s/def ::queue (s/and string?))

(s/def ::filter-type #{"id" "execute-fn-sym" "type"})
(s/def ::job-id uuid?)
(s/def ::filter-value-sym string?)
(s/def ::filter-value-type #{"unexecuted" "failed"})
(s/def ::limit nat-int?)
(s/def ::encoded-job string?)
(s/def ::encoded-jobs (s/coll-of ::encoded-job))

(defn str->long [str]
  (if (= (type str) Long)
    str
    (try (Long/parseLong str)
         (catch Exception _ :clojure.spec.alpha/invalid))))

(defn ->coll [x]
  (if (sequential? x)
    x
    (conj [] x)))

(defn validate-or-default
  ([spec ip] (validate-or-default spec ip ip))
  ([spec ip op] (validate-or-default spec ip op nil))
  ([spec ip op default]
   (if (s/valid? spec ip)
     op
     default)))
