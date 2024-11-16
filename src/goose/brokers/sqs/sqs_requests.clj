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
(defn size
  "Returns the approximate* size of the queue."
  [client queue-url]
  (let [attributes (aws/invoke client {:op      :GetQueueAttributes
                                       :request {:QueueUrl       queue-url
                                                 :AttributeNames ["ApproximateNumberOfMessages"]}})]
    (Integer/parseInt (get-in attributes [:Attributes "ApproximateNumberOfMessages"] "0"))))

(defn purge
  "Purges the queue."
  [client queue-url]
  (aws/invoke client {:op      :PurgeQueue
                      :request {:QueueUrl queue-url}}))
