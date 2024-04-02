(ns goose.brokers.redis.commands-test
  (:require
    [goose.brokers.redis.commands :as redis-cmds]
    [goose.test-utils :as tu]

    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [taoensso.carmine :as car]))

(use-fixtures :each tu/redis-fixture)

(deftest scan-seq-test
  (testing "scanning over all keys"
    (let [ks (map #(str "foo" %) (range 5000))]
      (doseq [k ks]
        (redis-cmds/wcar* tu/redis-conn
          (car/set k 42)))
      (is (= (set ks)
             (set (redis-cmds/scan-seq tu/redis-conn))))))

  (testing "scanning over keys starting with foo"
    (let [foo-keys (map #(str "foo" %) (range 5000))
          bar-keys (map #(str "bar" %) (range 5000))]
      (doseq [k (concat foo-keys bar-keys)]
        (redis-cmds/wcar* tu/redis-conn
          (car/set k 42)))

      (is (= (set foo-keys)
             (set (redis-cmds/scan-seq tu/redis-conn
                                       (fn [conn _ cursor]
                                         (redis-cmds/wcar* conn
                                           (car/scan cursor "MATCH" "foo*" "COUNT" 1)))))))))

  (testing "scanning a data structure at a particular key"
    (let [set-members (set (map #(str "foo" %) (range 5000)))]
      (doseq [member set-members]
        (redis-cmds/wcar* tu/redis-conn
          (car/sadd "my-set" member)))

      (is (= set-members
             (set (redis-cmds/scan-seq tu/redis-conn
                                       (fn [conn redis-key cursor]
                                         (redis-cmds/wcar* conn
                                           (car/sscan redis-key cursor "MATCH" "*" "COUNT" 1)))
                                       "my-set")))))))

(deftest find-in-set-test
  (testing "finding a member in a set"
    (let [foo-members (map #(str "foo" %) (range 1000))
          bar-members (map #(str "bar" %) (range 1000))]
      (doseq [member (concat foo-members bar-members)]
        (redis-cmds/wcar* tu/redis-conn
          (car/sadd "my-set" member)))

      (is (= (set foo-members)
             (set (redis-cmds/find-in-set tu/redis-conn
                                          "my-set"
                                          #(str/starts-with? % "foo"))))))))

(deftest list-seq-test
  (testing "iterating over a list from right to left"
    (let [list-members (map #(str "foo" %) (range 5))]
      (doseq [member list-members]
        (redis-cmds/enqueue-back tu/redis-conn "my-list" member))

      (is (= list-members
             (redis-cmds/list-seq tu/redis-conn "my-list"))))))

(deftest list-position-test
  (testing "iterating over a list from start to stop"
    (let [list-members (map #(str "foo" %) (range 5))]
      (doseq [member list-members]
        (redis-cmds/enqueue-front tu/redis-conn "my-list" member))
      (is (= [0] (redis-cmds/list-position tu/redis-conn "my-list" "foo0")))
      (is (= [2 1] (redis-cmds/list-position tu/redis-conn "my-list" "foo2" "foo1"))))))

(deftest range-from-front-test
  (testing "should get the range from the front of the queue"
    (doseq [member ["foo0" "foo1" "foo2" "foo3" "foo4"]]
      (redis-cmds/enqueue-back tu/redis-conn "my-list" member))
    (is (= ["foo0" "foo1" "foo2"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 2)))
    (is (= ["foo0" "foo1" "foo2" "foo3" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 4)))
    (is (= ["foo0"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 0)))
    (is (= ["foo2" "foo3"] (redis-cmds/range-from-front tu/redis-conn "my-list" 2 3)))
    (is (= ["foo2" "foo3" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 2 6)))))

(deftest del-from-list-test
  (testing "should delete multiple members from list"
    (let [list-members (map #(str "foo" %) (range 5))]
      (doseq [member list-members]
        (redis-cmds/enqueue-back tu/redis-conn "my-list" member)))
    (is (= [1 1 0] (redis-cmds/del-from-list tu/redis-conn "my-list" "foo0" "foo1" "abc")))
    (is (= ["foo2" "foo3" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 2)))

    (is (= [1] (redis-cmds/del-from-list tu/redis-conn "my-list" "foo3")))
    (is (= ["foo2" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 1)))))

(deftest del-from-list-and-enqueue-front-test
  (testing "should prioritise execution of multiple jobs"
    (let [list-members (map #(str "foo" %) (range 5))]
      (doseq [member list-members]
        (redis-cmds/enqueue-back tu/redis-conn "my-list" member)))
    (is (= ["foo0" "foo1" "foo2" "foo3" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 4)))
    (is (= [[["OK" "QUEUED" "QUEUED"] [1 5]] [["OK" "QUEUED" "QUEUED"] [1 5]]]
           (redis-cmds/del-from-list-and-enqueue-front tu/redis-conn "my-list" "foo2" "foo0")))
    (is (= ["foo0" "foo2" "foo1" "foo3" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 4)))

    (is (= [[["OK" "QUEUED" "QUEUED"] [1 5]]](redis-cmds/del-from-list-and-enqueue-front tu/redis-conn "my-list" "foo3")))
    (is (= ["foo3" "foo0" "foo2" "foo1" "foo4"] (redis-cmds/range-from-front tu/redis-conn "my-list" 0 4)))))

(deftest find-in-list-test
  (testing "finding list members that match the given predicate"
    (let [foo-members (map #(str "foo" %) (range 1000))
          bar-members (map #(str "bar" %) (range 1000))]
      (doseq [member (concat foo-members bar-members)]
        (redis-cmds/enqueue-back tu/redis-conn "my-list" member))

      (is (= bar-members
             (redis-cmds/find-in-list tu/redis-conn
                                      "my-list"
                                      #(str/starts-with? % "bar")
                                      2000))))))

(deftest find-in-sorted-set-test
  (testing "finding sorted set members that match the given predicate"
    (let [foo-members (set (map #(str "foo" %) (range 1000)))
          bar-members (set (map #(str "bar" %) (range 1000)))]
      (doseq [member (concat foo-members bar-members)]
        (redis-cmds/wcar* tu/redis-conn
          (car/zadd "my-zset" (rand-int 100) member)))

      (is (= bar-members
             (set (redis-cmds/find-in-sorted-set tu/redis-conn
                                                 "my-zset"
                                                 #(str/starts-with? % "bar")
                                                 2000))))))

  (testing "limiting the number of sorted set members scanned"
    (let [foo-members (set (map #(str "foo" %) (range 1000)))]
      (doseq [member foo-members]
        (redis-cmds/wcar* tu/redis-conn
          (car/zadd "my-other-zset" (rand-int 100) member)))

      (is (= 500
             (count (redis-cmds/find-in-sorted-set tu/redis-conn
                                                   "my-other-zset"
                                                   #(str/starts-with? % "foo")
                                                   500)))))))
