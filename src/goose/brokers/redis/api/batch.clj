(ns goose.brokers.redis.api.batch
  (:require [goose.brokers.redis.batch :as batch]))

(defn status [redis-conn id]
  (batch/get-batch-state redis-conn id))
