(ns goose.utils
  (:require
    [clojure.tools.logging :as log]))

(defn wrap-error [error data]
  {:errors {error data}})

(defmacro log-on-exceptions
  "Catch any Exception from the body and log it."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (log/error (.toString e#))
       (log/error "Exception occurred at:"
                (-> e#
                    (Throwable->map)
                    (get :via)
                    (first)
                    (get :at))))))
