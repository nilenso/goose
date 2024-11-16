(ns goose.brokers.sqs.broker
  (:require
   [goose.broker :as b]
   [goose.brokers.sqs.worker :as sqs-worker]
   [goose.brokers.sqs.sqs-requests :as sqs-request]
   [cognitect.aws.client.api :as aws]))

(defprotocol Close
  "Closes connection to SQS Message Broker."
  (close [this]))

(defrecord SQS [client queue-url opts]
  b/Broker
  ;; SQS supports nothing more than:
  ;;   - Enqueue a job
  ;;   - Start a worker process
  ;;   - Enqueued jobs API
  ;;   - Batch jobs (Will implement later)

  ;; Enqueue a job for immediate execution
  (enqueue
    [_ job]
    (sqs-request/enqueue client queue-url job))

  ;; Start a worker process
  (start-worker
    [_ worker-opts]
    (sqs-worker/start client queue-url worker-opts))

  ;; Enqueued jobs API
  (enqueued-jobs-size
    [_ _]
    (sqs-request/size client queue-url))

  (enqueued-jobs-purge
    [_ _]
    (sqs-request/purge client queue-url))

  Close
  (close [_]
    (println "SQS client does not require explicit closure.")))

(def default-opts
  {:region "ap-south-1"})

(defn new-producer
  "Creates an SQS broker implementation for producer (client).

  ### Args
  `opts` : Map of AWS client options.

  ### Usage
  ```clojure
  (new-producer {:region \"us-east-1\" :queue-url \"https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue\"})
  ```"
  [opts]
  (let [client    (aws/client {:api :sqs :region (:region opts)})
        queue-url (:queue-url opts)]
    (->SQS client queue-url opts)))

(defn new-consumer
  "Creates an SQS broker implementation for worker (consumer).

  ### Args
  `opts` : Map of AWS client options.

  ### Usage
  ```clojure
  (new-consumer {:region \"us-east-1\" :queue-url \"https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue\"})
  ```"
  [opts]
  (let [client    (aws/client {:api :sqs :region (:region opts)})
        queue-url (:queue-url opts)]
    (->SQS client queue-url opts)))
