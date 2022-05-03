(ns goose.client
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.spec.alpha :as s]
    [goose.worker :as w]
    [failjure.core]
    [metis.core]
    [validateur.validation :refer :all]))

(defn foo [bar] (println bar))

(defn non-negative? [x]
  (not (neg? x))) ; retries should be non-negative.
((defn validation [retries b]
   (assert (non-negative? retries))
   (assert (even? b))) 1 1)


; https://github.com/bhb/expound try this.
; alternatively, write own assertion fn.
; use explain-str
; 1 spec for each symbol, and 1 spec for whole thing.
; :client -> :goose.client
; ns-present -> qualified symbol? or namespace
; disallow strings. only allow symbol fns.
; document allowed things
(def default-queue "goose/queue:default")

(def supported-arg-types
  #{java.lang.Number ; int, long,
    java.lang.constant.Constable ; string, character
    java.lang.Boolean ; true/false
    ;
    })
(defn serializable? [arg]
  (< 0 (count (clojure.set/intersection supported-arg-types (parents (type arg))))))
((defn args-serializable? [args]
   (if (empty? args)
     true
     (if (serializable? (first args))
       (recur (rest args))
       false))) '(1 0.1 "a" \a true :a))

(s/def :client/retries-non-negative? #(>= % 0)) ;Retries should be >= 0.
(s/def :client/ns-present? #(.contains % "/"))
(s/def :client/fn-resolvable? #(resolve %))
(s/def :client/args-serializable? #(boolean %))

(s/def ::async-spec
  ())
(use 'metis.core)
(defvalidator user-validator
              [:first-name :presence])
(foo (user-validator {:first-name "aks"}))

(validation-set
  (presence-of :email))

(s/def :deck/suit #{:club :diamond :heart :spade})
(comment (foo (s/explain :deck/suit :club)))

(defn validate-fields
  [fn-symbol args retries]
  (and
    (s/valid? :client/ns-present? fn-symbol)
    (s/valid? :client/fn-resolvable? fn-symbol)
    (s/assert :client/retries-non-negative? retries)
    (s/valid? :client/args-serializable? args)))

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  TODO: Fill details once interface is finalized."
  ([fn-symbol & {:keys [args retries]
                 :or   {args    nil
                        retries 0}}]
   {:pre [(validate-fields (str fn-symbol) args retries)]}
   (let [f (str fn-symbol)]
     (r/wcar* (car/rpush default-queue [f args])))))

(comment
  (async `foo {:args '(1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2 true) :retries -1})
  (async "foo-bar" {:args '(1 {:a "b"} [1 2 3] '(1 2 3) "2" 6.2) :retries 2})
  (w/worker default-queue)
  (str #(println 1))
  (str `foo))