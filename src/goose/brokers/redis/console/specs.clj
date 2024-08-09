(ns ^:no-doc goose.brokers.redis.console.specs
  (:require [clojure.spec.alpha :as s])
  (:import
    (java.lang Long)))

(s/def ::page pos-int?)
(s/def ::queue string?)

(s/def ::enqueued-filter-type #{"id" "execute-fn-sym" "type"})
(s/def ::dead-filter-type #{"id" "execute-fn-sym" "queue"})
(s/def ::scheduled-filter-type #{"id" "execute-fn-sym" "type" "queue"})
(s/def ::batch-filter-type #{"id"})
(s/def ::periodic-filter-type #{"name"})

(s/def ::job-id uuid?)
(s/def ::cron-name string?)
(s/def ::cron-names (s/coll-of ::cron-name))
(s/def ::filter-value-sym string?)
(s/def ::filter-value-type #{"unexecuted" "failed" "scheduled"})
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

(defn validate-req-params [{:keys [id queue job jobs cron-names]}]
  {:id           (validate-or-default ::job-id (-> id str parse-uuid) id)
   :queue        (validate-or-default ::queue queue)
   :encoded-job  (validate-or-default ::encoded-job job job)
   :encoded-jobs (validate-or-default ::encoded-jobs (->coll jobs) (->coll jobs))
   :cron-names   (validate-or-default ::cron-names (->coll cron-names) (->coll cron-names))})
