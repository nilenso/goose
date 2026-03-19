(ns goose.brokers.sqs.worker
  (:require
   [cognitect.aws.client.api :as aws]
   [clojure.core.async :as async]
   [goose.utils :as u]
   [goose.consumer :as c]))

(defn start
  "Polls messages from an SQS queue, executes each message as a job, and deletes it upon success.
  
  Args:
  `client` : AWS SQS client.
  `queue-url` : URL of the SQS queue.
  `opts` : Additional options required for job execution."
  [client queue-url opts]
  (let [continue? (atom true)]
    (async/go-loop []
      (when @continue?
        (let [response (aws/invoke client {:op :ReceiveMessage
                                           :request {:QueueUrl queue-url
                                                     :MaxNumberOfMessages 1
                                                     :WaitTimeSeconds 10}})]
          (when-let [messages (:Messages response)]
            (doseq [message messages]

              (let [{:keys [Body ReceiptHandle]} message
                    job (u/decode-from-str Body)]
                (try
                  (c/execute-job opts job)
                  (aws/invoke client {:op :DeleteMessage
                                      :request {:QueueUrl queue-url
                                                :ReceiptHandle ReceiptHandle}})
                  (catch Exception e
                    (println "Error executing job:" e))))))
          (recur))))
    (fn stop-polling []
      (reset! continue? false))))
