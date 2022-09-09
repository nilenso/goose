(ns goose.brokers.redis.cron.registry-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [goose.brokers.redis.cron.registry :as cron-registry]
            [goose.job :as j]
            [goose.defaults :as d]
            [goose.retry :as retry]
            [goose.utils :as u]
            [goose.cron.parsing :as cron-parsing]
            [goose.test-utils :as tu]
            [goose.api.enqueued-jobs :as enqueued-jobs]))

(use-fixtures :each tu/redis-fixture)

(defn- after-due-time [cron-schedule]
  (inc
    (cron-parsing/next-run-epoch-ms
      cron-schedule)))

(defn- before-due-time [cron-schedule]
  (dec
    (cron-parsing/next-run-epoch-ms
      cron-schedule)))

(deftest cron-registration-test
  (testing "Registering two cron entries with the same name"
    (cron-registry/register-cron tu/redis-conn
                                 "my-cron-name"
                                 "*/5 * * * *"
                                 (j/description `foo-sym
                                                [:a "b" 3]
                                                "foo-queue"
                                                (d/prefix-queue "foo-queue")
                                                retry/default-opts))
    (cron-registry/register-cron tu/redis-conn
                                 "my-cron-name"
                                 "* * * * *"
                                 (j/description `bar-sym
                                                [:a "b" 3]
                                                "foo-queue"
                                                (d/prefix-queue "foo-queue")
                                                retry/default-opts))
    (is (= {:cron-schedule   "* * * * *"
            :name            "my-cron-name"
            :job-description {:args           [:a "b" 3]
                              :execute-fn-sym `bar-sym
                              :prefixed-queue (d/prefix-queue "foo-queue")
                              :queue          "foo-queue"
                              :retry-opts     retry/default-opts}}
           (cron-registry/find-by-name tu/redis-conn "my-cron-name"))
        "The cron entry exists and was overwritten")))

(deftest due-cron-entries-test
  (testing "Checking if cron entries are due"
    (let [_cron-entry (cron-registry/register-cron tu/redis-conn
                                                   "my-cron-name"
                                                   "*/5 * * * *"
                                                   (j/description `foo-sym
                                                                  [:a "b" 3]
                                                                  "foo-queue"
                                                                  (d/prefix-queue "foo-queue")
                                                                  retry/default-opts))]

      (with-redefs [u/epoch-time-ms (constantly
                                      (after-due-time
                                        "*/5 * * * *"))]
        (is (= [{:cron-schedule   "*/5 * * * *"
                 :name            "my-cron-name"
                 :job-description {:args           [:a "b" 3]
                                   :execute-fn-sym `foo-sym
                                   :prefixed-queue (d/prefix-queue "foo-queue")
                                   :queue          "foo-queue"
                                   :retry-opts     retry/default-opts}}]
               (cron-registry/due-cron-entries tu/redis-conn))
            "The cron entry is due after the scheduled cron time"))

      (with-redefs [u/epoch-time-ms (constantly
                                      (before-due-time
                                        "*/5 * * * *"))]
        (is (empty? (cron-registry/due-cron-entries tu/redis-conn))
            "The cron entry is not due before the scheduled cron time")))))

(deftest find-and-enqueue-cron-entries-test
  (testing "find-and-enqueue-cron-entries"
    (is (not (cron-registry/enqueue-due-cron-entries tu/redis-conn))
        "find-and-enqueue-cron-entries returns falsey if due cron entries were not found")

    (cron-registry/register-cron tu/redis-conn
                                 "my-cron-name"
                                 "*/5 * * * *"
                                 (j/description `foo-sym
                                                [:a "b" 3]
                                                "foo-queue"
                                                (d/prefix-queue "foo-queue")
                                                retry/default-opts))
    (with-redefs [u/epoch-time-ms                (constantly
                                                   (after-due-time
                                                     "*/5 * * * *"))
                  cron-parsing/next-run-epoch-ms (constantly
                                                   (inc
                                                     (after-due-time
                                                       "*/5 * * * *")))]
      (is (cron-registry/enqueue-due-cron-entries tu/redis-conn)
          "find-and-enqueue-cron-entries returns truthy if due cron entries were found"))

    (is (empty?
          (with-redefs [u/epoch-time-ms (constantly
                                          (after-due-time
                                            "*/5 * * * *"))]
            (cron-registry/due-cron-entries tu/redis-conn)))
        "The cron entry is not immediately due after enqueueing")

    (is (= {:args           [:a "b" 3]
            :execute-fn-sym `foo-sym
            :prefixed-queue (d/prefix-queue "foo-queue")
            :queue          "foo-queue"
            :retry-opts     retry/default-opts}
           (-> (enqueued-jobs/find-by-pattern tu/redis-broker
                                              "foo-queue"
                                              (fn [{:keys [execute-fn-sym]}]
                                                (= `foo-sym execute-fn-sym)))
               first
               (dissoc :id :enqueued-at)))
        "A job is created from the cron entry and enqueued")))
