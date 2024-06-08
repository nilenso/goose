(ns goose.console-client
  (:require
    [clojure.tools.namespace.repl :refer [refresh]]
    [compojure.core :refer [context defroutes]]
    [compojure.route :as route]
    [goose.brokers.redis.broker :as redis]
    [goose.brokers.rmq.broker :as rmq]
    [goose.client :as c]
    [goose.console :as console]

    [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn redis-url []
  (let [host (or (System/getenv "GOOSE_REDIS_HOST") "localhost")
        port (or (System/getenv "GOOSE_REDIS_PORT") "6379")]
    (str "redis://" host ":" port)))

(defn rmq-url []
  (let [host (or (System/getenv "GOOSE_RABBITMQ_HOST") "localhost")
        port (or (System/getenv "GOOSE_RABBITMQ_PORT") "5672")
        username (or (System/getenv "GOOSE_RABBITMQ_USERNAME") "guest")
        password (or (System/getenv "GOOSE_RABBITMQ_PASSWORD") "guest")]
    (str "amqp://" username ":" password "@" host ":" port)))

(defn brokers [broker]
  (get {:redis    (redis/new-producer
                    (merge redis/default-opts {:url (redis-url)}))
        :rabbitmq (rmq/new-producer (merge rmq/default-opts
                                           {:settings {:uri (rmq-url)}}))}
       (keyword broker)))

(defn console-opts [broker app-name]
  {:broker       (brokers broker)
   :app-name     app-name
   :route-prefix ""})

(defonce server (atom nil))

(defn routes [console-opts]

  (defroutes goose-routes
             (context (:route-prefix console-opts) []
                      (partial console/app-handler console-opts))
             (route/not-found "<h1>Page not found </h1>")))

(defn start-server [& _]
  (let [broker (or (System/getenv "GOOSE_BROKER") "redis")
        app-name (or (System/getenv "GOOSE_APPNAME") "Goose client")
        console-opts (console-opts broker app-name)
        port (or (System/getenv "GOOSE_PORT") 3000)]
    (println "Starting server!!")
    (reset! server (jetty/run-jetty (routes console-opts)
                                    {:port  port
                                     :join? false}))))


(defn redis-enqueued-jobs []
  (let [redis-producer (redis/new-producer
                         (merge redis/default-opts {:url (redis-url)}))
        client-opts (assoc c/default-opts
                      :broker redis-producer)]
    (c/perform-async client-opts `my-fn "foo" :nar)
    (c/perform-async client-opts `prn nil "foo" \q ["a" 1 2] {"a"    "b"
                                                              1      :2
                                                              2      3
                                                              true   234
                                                              "true" false
                                                              "p"    \p})
    (mapv #(c/perform-async (assoc c/default-opts
                              :queue "random"
                              :broker redis-producer) `prn "foo" %) (range 22))
    (c/perform-async (assoc c/default-opts
                       :queue "long-queue-name-exceeding-10-chars"
                       :broker redis-producer) `prn "foo" :bar)))

(defn -main [& args]
  (let [func (first args)]
    (case func
      "redis-enqueued-jobs" (doall (redis-enqueued-jobs)
                                   (System/exit 1))
      (start-server))))

(defn stop-server []
  (when-let [s @server]
    (.stop s)
    (println s "Stopped server")))

(defn restart []
  (stop-server)
  (refresh :after 'goose.goose-client/start-server))
