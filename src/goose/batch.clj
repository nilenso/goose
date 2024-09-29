(ns goose.batch
  (:require
   [goose.job :as job]
   [goose.utils :as u]

   [clojure.tools.logging :as log]))

(defn construct-args
  "A utility function to construct a collection of args for batch-jobs."
  [coll & args]
  (conj coll args))

(defn default-callback
  "Sample callback for a batch"
  [batch-id status]
  (log/infof "Batch: %s execution completed. Status: %s" batch-id status))

(def default-opts
  "Map of sample configs for batch-jobs.

  #### Mandatory Keys
  `:linger-sec`      : Number of seconds batch metadata will be preserved
  in message broker, after a batch has reached completion.

  `:callback-fn-sym` : A fully-qualified function symbol to report status of a batch.\\
  Takes `batch-id` and `status` as input.\\
  *Example*          : [[default-callback]]"
  {:linger-sec      3600
   :callback-fn-sym `default-callback})

(def status-in-progress :in-progress)
(def status-success :success)
(def status-dead :dead)
(def status-partial-success :partial-success)
(def ^:private terminal-states [status-success status-dead status-partial-success])

(defn ^:no-doc terminal-state? [status]
  (some #{status} terminal-states))

(defn ^:no-doc status-from-job-states
  [enqueued retrying success dead]
  (cond
    (< 0 (+ enqueued retrying)) status-in-progress
    (= 0 dead) status-success
    (= 0 success) status-dead
    :else status-partial-success))

(defn ^:no-doc new
  [{:keys [queue ready-queue retry-opts]}
   {:keys [callback-fn-sym linger-sec]}
   jobs]
  (let [id (str (random-uuid))]
    {:id              id
     :callback-fn-sym callback-fn-sym
     :linger-sec      linger-sec
     :queue           queue
     :ready-queue     ready-queue
     :retry-opts      retry-opts
     :jobs            (map #(assoc % :batch-id id) jobs)
     :total           (count jobs)
     :status          status-in-progress
     :created-at      (u/epoch-time-ms)}))

(defn ^:no-doc new-callback-job
  [{:keys [callback-fn-sym queue ready-queue retry-opts]} & args]
  (job/new callback-fn-sym args queue ready-queue retry-opts))
