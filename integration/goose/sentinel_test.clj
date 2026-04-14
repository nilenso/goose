(ns goose.sentinel-test
  (:require
   [clojure.test :refer [deftest is testing]]))


(deftest sentinel-truthiness-test
  (testing ::truthiness
    (is true)))

(deftest sentinel-falsehood-test
  (testing ::falsehood
    (is false)))
