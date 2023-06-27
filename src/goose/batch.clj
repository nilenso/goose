(ns goose.batch)

(defn new
  [{:keys [callback-fn-sym]} jobs]
  (let [id (str (random-uuid))]
    {:id              id
     :callback-fn-sym callback-fn-sym
     :jobs            (map #(assoc % :batch-id id) jobs)}))

