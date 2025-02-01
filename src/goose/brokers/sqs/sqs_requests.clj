(ns goose.brokers.sqs.sqs-requests
  (:require
   [cognitect.aws.client.api :as aws]
   [clojure.data.json :as json]))

(defn enqueue
  "Enqueues a job for immediate execution."
  [client queue-url job]
  (aws/invoke client {:op      :SendMessage
                      :request {:QueueUrl     queue-url
                                :MessageBody  (json/write-str job)
                                :DelaySeconds 0}}))
