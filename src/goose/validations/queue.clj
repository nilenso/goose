(ns goose.validations.queue
  (:require
    [goose.defaults :as d]
    [goose.utils :as u]
    [clojure.string :as str]))

(defn validate-queue
  [queue]
  (when-let
    [validation-error
     (cond
       (not (string? queue))
       ["Queue should be a string" (u/wrap-error :non-string-queue queue)]

       (< 1000 (count queue))
       ["Queue length should be less than 1000" (u/wrap-error :queue-len-gt-1000 queue)]

       (str/starts-with? queue d/queue-prefix)
       ["Queue shouldn't be prefixed" (u/wrap-error :prefixed-queue queue)]

       (clojure.string/includes? d/protected-queues queue)
       ["Protected queue names shouldn't be used" (u/wrap-error :prefixed-queue queue)])]
    (throw (apply ex-info validation-error))))
