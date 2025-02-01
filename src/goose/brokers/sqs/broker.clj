(ns goose.brokers.sqs.broker
  (:require
   [goose.broker :as b]
   [goose.brokers.sqs.worker :as sqs-worker]
   [goose.brokers.sqs.sqs-requests :as sqs-request]
   [cognitect.aws.client.api :as aws]
   [goose.utils :as u]))

(defprotocol Close
  "Closes connection to SQS Message Broker."
  (close [this]))

(defrecord SQS [client opts]
  b/Broker
  ;; SQS supports nothing more than:
  ;;   - Enqueue a job
  ;;   - Start a worker process
  ;;   - Enqueued jobs API
  ;;   - Batch jobs (Will implement later)

  (enqueue
    [this job]
    (let [{:keys [client]} this
          {:keys [queue-url]} opts]

      (sqs-request/enqueue client queue-url (u/encode-to-str job))))

  (start-worker
    [this worker-opts]
    (let [{:keys [client]} this
          {:keys [queue-url]} opts]
      (sqs-worker/start client queue-url worker-opts)))

  Close
  (close [_]
    (println "SQS client does not require explicit closure.")))

(def default-opts
  "Default Config is not allowed like redis or rmq
   
   ### Keys
   `:region`: AWS Region
   Example: \"ap-south-1\" , \"us-east-1\"
   
   `:queue-url`: The Queue URL provided by the AWS SQS
   Example: \"https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue\""
  {:region nil
   :queue-url nil})

(defn- create-sqs
  "Helper function that creates an SQS broker implementation.
  It abstracts the common logic for both producer and consumer."

  [opts]
  (let [client (aws/client {:api :sqs :region (:region opts)})]
    (->SQS client opts)))

(defn new-producer
  "Creates an SQS broker implementation for producer (client).

  ### Args
  `opts` : Map of AWS client options.

  ### Usage
  ```clojure
  (new-producer {:region \"us-east-1\" :queue-url \"https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue\"})
  ```"
  [opts]
  (create-sqs opts))

(defn new-consumer
  "Creates an SQS broker implementation for worker (consumer).

  ### Args
  `opts` : Map of AWS client options.

  ### Usage
  ```clojure
  (new-consumer {:region \"us-east-1\" :queue-url \"https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue\"})
  ```"
  [opts]
  (create-sqs opts))
