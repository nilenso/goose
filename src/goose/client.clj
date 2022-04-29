(ns goose.client
  (:require
    [goose.redis :as r]
    [taoensso.carmine :as car]
    [clojure.spec.alpha :as s]
    [clojure.string :as string]))

; - implement async
; - PARAMS:
; - NS+fn

; - SADD queue to goose/queues
; - if it's already present in Redis not as a set, throw an exception & exit
; - have a multi-arity where config object can be passed in?
; - Can clients use ` to avoid having to write NS?
; -  Schema:
; retries: (an integer, can be 0 for no retries)
; queue_name (necessary?)
; args
; job_id
; namespace+class
; created/updated/enqueued at

; QQQ: Maintain state that SADDs to "goose:queues" set only ONCE?
; - validations:
;     fn is serializable
;     NS is present
;     args are serializable
; - SADD (if not already present)
; - Serialize/structure details
; -
(def default-queue "goose/queue:default")

(s/def :client/retries #(<= 0 %)) ;Retries should be >= 0.
; QQQ Correct way to validate?
; 1. Diff between named & anonymous fn
; 2. Just check if / is present to validate if NS is there?
(s/def :client/fully-qualified-function #(.contains % "/"))
(s/def :client/namespace #(boolean (find-ns (symbol (first (string/split % #"/"))))))

(defn validate-fields
  [f args queue retries]
  (and
    (s/valid? :client/fully-qualified-function f)
    (s/valid? :client/retries retries)
    (s/valid? :client/namespace f)))

(defn async
  "Enqueues a function for asynchronous execution from an independent worker.
  TODO: Fill details once interface is finalized."
  ([f & {:keys [args queue retries]
         :or   {args    nil
                queue   default-queue
                retries 0}}]
   ; QQQ: How to bubble-up error messages from validate-fields?
   {:pre [(validate-fields (str f) args queue retries)]}
   (let [f (str f)]

     (r/wcar* (car/rpush queue [f args])))))

(defn foo [bar] (println bar))
(comment
  (async `foo {:retries 2})
  (str #(println 1))
  (str `foo))