ADR: Periodic Jobs
=============

Rationale
---------

- Goose isn't using a pooling library for channels
  - Channels don't need thread-safety. Different jobs can be enqueued/consumed over threads & `delivery-tag` keeps track of them
  - Instead, a simple random channel selection is good enough
  - If there's a way to do round-robin, that'll be better
- `clj-kondo` is used from CLI instead of a deps alias command
  - deps alias takes ~20 seconds to load & CLI is instant

Avoided Designs
---------

- Because only 1 thread can access a channel at one time, channel pooling performed poorly when benchmarking
  - `validate/destroy` hooks aren't working :(
    - https://github.com/kul/pool/issues/3
  - Code here for future reference
```clojure
(defn create-pool [rmq-conn count]
  (doto (pool/get-pool (fn [] (rmq-channel/open rmq-conn {} (fn [x] x)))
                       {:validate int? :destroy lch/close})
    (.setTimeBetweenEvictionRunsMillis 1)
    (.setMinIdle count)
    (.setMaxIdle count)
    (.setMaxTotal count)))

(defmacro with-channel
  [pool [channel] & body]
  `(let [~channel (pool/borrow ~pool)]
     (try
       (do ~@body)
       (finally
         (pool/return ~pool ~channel)))))

(comment
  (let [rmq-conn (lcore/connect)
        my-pool (create-pool rmq-conn 2)
        thread-pool (cp/threadpool 5)]
    (doall (for [i (range 6)]
             (cp/future thread-pool
                        (with-channel my-pool [ch]
                                      (println "Holding back channel:" ch)
                                      (Thread/sleep 2000)
                                      (println "slept for thread:" i)))))
    (println "Above code takes 6 seconds to complete (2000*3)")))
```
