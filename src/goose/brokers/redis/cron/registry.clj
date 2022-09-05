(ns goose.brokers.redis.cron.registry
  (:require [goose.defaults :as d]
            [goose.brokers.redis.commands :as redis-cmds]
            [taoensso.carmine :as car]
            [goose.job :as j]
            [goose.cron.parsing :as cron-parsing]
            [goose.utils :as u]))

(defn registry-entry [cron-name cron-schedule job-description]
  {:name            cron-name
   :cron-schedule   cron-schedule
   :job-description job-description})

(defn- scan-sorted-set-command [sorted-set cursor]
  (car/zscan sorted-set cursor "MATCH" "*" "COUNT" 1))

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

(defn find-entry
  [conn cron-name]
  (first (filter #(= cron-name (:name %))
                 (items-seq conn d/prefixed-cron-schedules-queue))))

(defn register-cron
  "Registers a cron entry in Redis.
  If an entry already exists against the same name, it will be
  overwritten."
  [conn cron-name cron-schedule job-description]
  (redis-cmds/with-transaction conn
    (car/watch d/cron-entry-names-key)
    (car/watch d/prefixed-cron-schedules-queue)
    (let [name-exists?   (= 1 (car/with-replies (car/sismember d/cron-entry-names-key cron-name)))
          existing-entry (when name-exists?
                           (find-entry conn cron-name))]
      (car/multi)
      (when (and name-exists? existing-entry)
        (car/zrem d/prefixed-cron-schedules-queue existing-entry))
      (let [entry             (registry-entry cron-name cron-schedule job-description)
            next-run-epoch-ms (cron-parsing/next-run-epoch-ms cron-schedule)]
        (car/sadd d/cron-entry-names-key cron-name)
        (car/zadd d/prefixed-cron-schedules-queue
                  next-run-epoch-ms
                  entry)
        entry))))

(defn- set-due-time [cron-entry]
  (car/zadd d/prefixed-cron-schedules-queue
            "XX"
            "GT"
            (-> cron-entry
                :cron-schedule
                cron-parsing/next-run-epoch-ms)
            cron-entry))

(defn- enqueue-jobs-to-ready-on-priority [jobs]
  (doseq [[queue-key grouped-jobs] (group-by :prefixed-queue jobs)]
    (apply car/rpush queue-key grouped-jobs)))

(defn- due-cron-entries-command []
  (car/zrangebyscore d/prefixed-cron-schedules-queue
                     redis-cmds/sorted-set-min
                     (u/epoch-time-ms)
                     "limit"
                     0
                     d/cron-entries-pop-limit))

(defn due-cron-entries
  [redis-conn]
  (not-empty
    (redis-cmds/wcar* redis-conn
      (due-cron-entries-command))))

(defn enqueue-due-cron-entries
  "Returns truthy if due cron entries were found."
  [redis-conn]
  (redis-cmds/with-transaction redis-conn
    (car/watch d/prefixed-cron-schedules-queue)
    (let [due-cron-entries (not-empty (car/with-replies (due-cron-entries-command)))]
      ;; The `multi` call must be in this exact position.
      ;; It cannot be before the call to `due-cron-entries-command`.
      ;; It cannot be inside `when` or any conditional, because the transaction body
      ;; must contain a call to `multi` in all code paths.
      (car/multi)
      (when due-cron-entries
        (let [jobs-to-enqueue (map (comp j/from-description :job-description) due-cron-entries)]
          (enqueue-jobs-to-ready-on-priority jobs-to-enqueue)
          (doseq [cron-entry due-cron-entries]
            (set-due-time cron-entry)))
        true))))