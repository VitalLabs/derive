(defproject com.vitalreactor/derive "0.2.1"
  :description "Clojurescript library to support efficient computation of up to date values derived from a mutable data source.  Designed to integrate with functional UI frameworks like Om and with data stores like Datascript and NativeStore"
  :url "http://github.com/vitalreactor/derive"
  :license {:name "MIT License"
            :url "http://github.com/vitalreactor/derive/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [prismatic/schema "1.0.0"]]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds
              [ {:id "test"
                 :source-paths ["src" "test"]
                 :compiler {:output-to "resources/test/js/testable.js"
                            :output-dir "resources/test/js/out"
                            :output-map "resources/test/js/testable.js.map"
                            :parallel-build true
                            :optimizations :whitespace
                            :recompile-dependents false
                            :pretty-print true}}]
              :test-commands {"all" ["phantomjs" "test/phantomjs.js" "resources/test/index.html"]}})

