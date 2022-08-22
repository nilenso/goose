(ns goose.brokers.redis.cron.registry-test
  (:require [clojure.test :refer :all]
            [goose.brokers.broker :as broker]
            [goose.brokers.redis.cron.registry :as cron-registry]
            [goose.job :as j]
            [goose.defaults :as d]
            [goose.retry :as retry]
            [goose.utils :as u]
            [goose.cron.parsing :as cron-parsing]
            [goose.test-utils :as tu]
            [goose.api.enqueued-jobs :as enqueued-jobs]))

(use-fixtures :each tu/fixture)

(defn- after-due-time [cron-schedule]
  (inc
    (cron-parsing/next-run-epoch-ms
      cron-schedule)))

(defn- before-due-time [cron-schedule]
  (dec
    (cron-parsing/next-run-epoch-ms
      cron-schedule)))

(deftest cron-registry-test
  (testing "Registering, checking and enqueueing cron entries"
    (let [broker     (broker/new tu/redis-opts)
          cron-entry (cron-registry/register-cron broker
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
                 :id              (:id cron-entry)
                 :job-description {:args           [:a "b" 3]
                                   :execute-fn-sym `foo-sym
                                   :prefixed-queue (d/prefix-queue "foo-queue")
                                   :queue          "foo-queue"
                                   :retry-opts     retry/default-opts}}]
               (cron-registry/due-cron-entries broker))
            "The cron entry is due after the scheduled cron time"))

      (with-redefs [u/epoch-time-ms (constantly
                                      (before-due-time
                                        "*/5 * * * *"))]
        (is (nil? (cron-registry/due-cron-entries broker))
            "The cron entry is not due before the scheduled cron time"))

      (with-redefs [u/epoch-time-ms                (constantly
                                                     (after-due-time
                                                       "*/5 * * * *"))
                    cron-parsing/next-run-epoch-ms (constantly
                                                     (inc
                                                       (after-due-time
                                                         "*/5 * * * *")))]
        (when-let [entries (cron-registry/due-cron-entries broker)]
          (cron-registry/enqueue-due-cron-entries broker entries)))

      (is (nil?
            (with-redefs [u/epoch-time-ms (constantly
                                            (after-due-time
                                              "*/5 * * * *"))]
              (cron-registry/due-cron-entries broker)))
          "The cron entry is not immediately due after enqueueing")

      (is (= {:args           [:a "b" 3]
              :execute-fn-sym `foo-sym
              :prefixed-queue (d/prefix-queue "foo-queue")
              :queue          "foo-queue"
              :retry-opts     retry/default-opts}
             (-> (enqueued-jobs/find-by-pattern tu/redis-opts
                                                "foo-queue"
                                                (fn [{:keys [execute-fn-sym]}]
                                                  (= `foo-sym execute-fn-sym)))
                 first
                 (dissoc :id :enqueued-at)))
          "A job is created from the cron entry and enqueued")))

  (testing "find-and-enqueue-cron-entries"
    (testing "Registering, checking and enqueueing cron entries"
      (let [broker     (broker/new tu/redis-opts)]
        (cron-registry/register-cron broker
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
          (is (cron-registry/find-and-enqueue-cron-entries broker)
              "find-and-enqueue-cron-entries returns truthy if due cron entries were found"))

        (is (= {:args           [:a "b" 3]
                :execute-fn-sym `foo-sym
                :prefixed-queue (d/prefix-queue "foo-queue")
                :queue          "foo-queue"
                :retry-opts     retry/default-opts}
               (-> (enqueued-jobs/find-by-pattern tu/redis-opts
                                                  "foo-queue"
                                                  (fn [{:keys [execute-fn-sym]}]
                                                    (= `foo-sym execute-fn-sym)))
                   first
                   (dissoc :id :enqueued-at)))
            "A job is created from the cron entry and enqueued")))))
