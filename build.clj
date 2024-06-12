(ns build
    (:refer-clojure :exclude [test])
    (:require
        [clojure.tools.build.api :as b]))


(def class-dir "target/classes")
(def uber-file "target/goose-standalone.jar")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"
                                   :aliases [:console :test]})))

(defn clean []
      (b/delete {:path "target"}))

(defn uber [_]
      (clean)
      (b/copy-dir {:src-dirs   ["src" "resources/public" "console" "test"]
                   :target-dir class-dir})
      (b/compile-clj {:basis @basis
                      :src-dirs   ["src" "resources/public" "console" "test"]
                      :ns-compile '[goose.console-client]
                      :class-dir  class-dir})
      (b/uber {:class-dir class-dir
               :uber-file uber-file
               :basis @basis
               :main 'goose.console-client}))
