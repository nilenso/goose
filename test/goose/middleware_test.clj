(ns goose.middleware-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [are deftest]]
            [goose.specs :as specs]))

(def non-function-middleware :not-a-function)
(declare unbound-middleware)

(deftest middleware-spec-test
  (are [middleware valid?]
       (= valid? (s/valid? ::specs/middlewares middleware))
    identity true
    #'identity true
    #'non-function-middleware false
    #'unbound-middleware false
    nil false
    :middleware false
    {} false
    [] false))
