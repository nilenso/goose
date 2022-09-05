(ns goose.brokers.redis.cron.registry
  (:require [goose.defaults :as d]
            [goose.brokers.redis.commands :as redis-cmds]
            [taoensso.carmine :as car]
            [goose.job :as j]
            [goose.cron.parsing :as cron-parsing]
            [goose.utils :as u]))

(defn- scan-sorted-set-command [sorted-set cursor]
  (car/zscan sorted-set cursor "MATCH" "*" "COUNT" 1))

;; TODO: Move this to redis-cmds
(defn- items-seq
  ([conn sorted-set]
   (items-seq conn sorted-set 0))
  ([conn sorted-set cursor]
   (lazy-seq
     (let [[new-cursor-string replies] (redis-cmds/wcar* conn (scan-sorted-set-command sorted-set cursor))
           new-cursor (Integer/parseInt new-cursor-string)
           items      (map first (partition 2 replies))]
       (concat items
               (when-not (zero? new-cursor)
                 (items-seq conn sorted-set new-cursor)))))))

(defn registry-entry [cron-name cron-schedule job-description]
  {:name            cron-name
   :cron-schedule   cron-schedule
   :job-description job-description})

(defn find-entry
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
      (let [jobs-to-enqueue (map (comp j/from-description :job-description) due-cron-entries)]
        (enqueue-jobs-to-ready-on-priority jobs-to-enqueue)
        (doseq [cron-entry due-cron-entries]
          (set-due-time cron-entry)))
      true)))