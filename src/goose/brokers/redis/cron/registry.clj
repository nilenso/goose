(ns goose.brokers.redis.cron.registry
  {:no-doc true}
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.cron.parsing :as cron-parsing]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.utils :as u]

    [taoensso.carmine :as car]))

(defn registry-entry [cron-name cron-schedule job-description]
  {:name            cron-name
   :cron-schedule   cron-schedule
   :job-description job-description})

(defn find-by-name
  [conn cron-name]
  (redis-cmds/wcar* conn (car/hget d/cron-entries-hm-key cron-name)))

(defn- set-due-time [cron-entry]
  (car/zadd d/cron-schedules-zset-key
            (-> cron-entry
                :cron-schedule
                cron-parsing/next-run-epoch-ms)
            (:name cron-entry)))

(defn- persist-to-hash-map [{:keys [name] :as cron-entry}]
  (car/hmset d/cron-entries-hm-key name cron-entry))

(defn register-cron
  "Registers a cron entry in Redis.
  If an entry already exists against the same name, it will be
  overwritten."
  [conn cron-name cron-schedule job-description]
  (redis-cmds/with-transaction conn
    (car/watch d/cron-entries-hm-key)
    (car/watch d/cron-schedules-zset-key)
    (let [new-entry (registry-entry cron-name cron-schedule job-description)]
      (car/multi)
      (persist-to-hash-map new-entry)
      (set-due-time new-entry)
      new-entry)))

(defn- enqueue-jobs-to-ready-on-priority [jobs]
  (doseq [[queue-key grouped-jobs] (group-by :prefixed-queue jobs)]
    (apply car/rpush queue-key grouped-jobs)))

(defn- due-cron-names [redis-conn]
  (redis-cmds/wcar* redis-conn
    (car/zrangebyscore d/cron-schedules-zset-key
                       redis-cmds/sorted-set-min
                       (u/epoch-time-ms)
                       "limit"
                       0
                       d/cron-names-pop-limit)))

(defn- ensure-sequential [thing]
  (cond
    (sequential? thing) thing
    (nil? thing) []
    :else [thing]))

(defn due-cron-entries
  [redis-conn]
  (let [cron-names (due-cron-names redis-conn)]
    ;; ensure-sequential is necessary because if there's only one cron name,
    ;; wcar* will return nil or a single item instead of an empty/singleton list.
    (ensure-sequential
      (redis-cmds/wcar* redis-conn
        (doall (map (partial car/hget d/cron-entries-hm-key) cron-names))))))

(defn- create-job
  [{:keys [cron-schedule job-description] :as _cron-entry}]
  (-> (j/from-description job-description)
      (assoc :cron-run-at (cron-parsing/previous-run-epoch-ms cron-schedule))))

(defn enqueue-due-cron-entries
  "Returns truthy if due cron entries were found."
  [redis-conn]
  (redis-cmds/with-transaction redis-conn
    (car/watch d/cron-schedules-zset-key)
    (car/multi)
    (when-let [due-cron-entries (not-empty (due-cron-entries redis-conn))]
      ;; The `multi` call cannot be inside `when` or any conditional,
      ;; because the transaction body must contain a call to `multi`
      ;; in all code paths.
      (let [jobs-to-enqueue (map create-job due-cron-entries)]
        (enqueue-jobs-to-ready-on-priority jobs-to-enqueue)
        (doseq [cron-entry due-cron-entries]
          (set-due-time cron-entry)))
      true)))

(defn delete
  [redis-conn entry-name]
  (let [[_ atomic-results] (car/atomic redis-conn redis-cmds/atomic-lock-attempts
                             (car/multi)
                             (car/zrem d/cron-schedules-zset-key entry-name)
                             (car/hdel d/cron-entries-hm-key entry-name))]
    (= [1 1] atomic-results)))

(defn delete-all
  [redis-conn]
  (= 2 (redis-cmds/del-keys redis-conn [d/cron-entries-hm-key d/cron-schedules-zset-key])))
