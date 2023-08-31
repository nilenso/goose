(ns ^:no-doc goose.brokers.redis.cron
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.cron.parsing :as cron-parsing]
    [goose.defaults :as d]
    [goose.job :as j]
    [goose.utils :as u]

    [taoensso.carmine :as car])
  (:import
    (java.time ZoneId)))

(defn registry-entry
  [{:keys [cron-name cron-schedule timezone]
    :or   {timezone (.getId (ZoneId/systemDefault))}
    :as   _cron-opts}
   job-description]
  {:cron-name       cron-name
   :cron-schedule   cron-schedule
   :timezone        timezone
   :job-description job-description})

(defn size [redis-conn]
  (redis-cmds/wcar* redis-conn (car/hlen d/prefixed-cron-entries)))

(defn find-by-name [redis-conn cron-name]
  (redis-cmds/wcar* redis-conn (car/hget d/prefixed-cron-entries cron-name)))

(defn- set-due-time
  [{:keys [cron-name cron-schedule timezone]}]
  (car/zadd d/prefixed-cron-queue
            (cron-parsing/next-run-epoch-ms cron-schedule timezone)
            cron-name))

(defn- persist-to-hash-map [{:keys [cron-name] :as cron-entry}]
  (car/hmset d/prefixed-cron-entries cron-name cron-entry))

(defn register
  "Registers a cron entry in Redis.
  If an entry already exists against the same name, it will be
  overwritten."
  [redis-conn cron-opts job-description]
  (redis-cmds/with-transaction redis-conn
    (car/watch d/prefixed-cron-entries)
    (car/watch d/prefixed-cron-queue)
    (let [new-entry (registry-entry cron-opts job-description)]
      (car/multi)
      (persist-to-hash-map new-entry)
      (set-due-time new-entry)
      new-entry)))

(defn- enqueue-jobs-to-ready-on-priority
  [jobs]
  (doseq [[queue-key grouped-jobs] (group-by :ready-queue jobs)]
    (apply car/rpush queue-key grouped-jobs)))

(defn- due-cron-names
  [redis-conn]
  (redis-cmds/wcar* redis-conn
    (car/zrangebyscore d/prefixed-cron-queue
                       redis-cmds/sorted-set-min
                       (u/epoch-time-ms)
                       "limit"
                       0
                       d/redis-cron-names-pop-limit)))

(defn- ensure-sequential
  [thing]
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
        (doall (map #(car/hget d/prefixed-cron-entries %) cron-names))))))

(defn- create-job
  [{:keys [cron-schedule timezone job-description]
    :as   _cron-entry}]
  (-> (j/from-description job-description)
      (assoc :cron-run-at (cron-parsing/previous-run-epoch-ms cron-schedule timezone))))

(defn enqueue-due-cron-entries
  "Returns truthy if due cron entries were found."
  [redis-conn]
  (redis-cmds/with-transaction redis-conn
    (car/watch d/prefixed-cron-queue)
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
                             (car/zrem d/prefixed-cron-queue entry-name)
                             (car/hdel d/prefixed-cron-entries entry-name))]
    (= [1 1] atomic-results)))

(defn purge [redis-conn]
  (= 2 (redis-cmds/del-keys redis-conn d/prefixed-cron-entries d/prefixed-cron-queue)))
