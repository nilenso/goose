(ns goose.middleware)

(defn specimen
  [call]
  (fn [opts job]
    ; Pre-execution tasks.
    ; Be careful when modifying fields of opts/job.
    (call opts job)
    ; Post-execution tasks.
    ))
