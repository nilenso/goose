(ns goose.brokers.redis.console
  (:require [ring.util.response :as response]))

(defn handler [_broker _req]
  (case uri
    "/" (response/not-found "<div> Redis Homepage </div>")
    (response/not-found "<div> Not found </div>")))
