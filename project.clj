(defproject com.vitalreactor/derive "0.1.0"
  :description "An interchange format for describing schedules as immutable values in Clojure and ClojureScript"
  :url "http://github.com/vitalreactor/derive"
  :license {:name "MIT License"
            :url "http://github.com/vitalreactor/derive/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [datascript "0.1.5"]
                 [prismatic/schema "0.2.2"]
                 [vitalreactor/om "0.6.3.1"]]
  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.2.2"]]
                   :cljsbuild {:builds [{:source-paths ["target/src" "target/test"]
                                         :compiler {:output-to "target/js/schedule_test.js"
                                                    :optimizations :whitespace
                                                    :pretty-print true}}]
                               :test-commands {"unit-tests" ["phantomjs" :runner "target/js/schedule_test.js"]}}}}
  :plugins [[com.keminglabs/cljx "0.3.2"]
            [lein-cljsbuild "1.0.2"]
            [com.cemerick/clojurescript.test "0.2.2"]]
  :source-paths ["src" "target/src"]
  :test-paths ["test" "target/test"]
  :cljx {:builds [{:source-paths ["cljx/src"]
                       :output-path "target/src"
                       :rules :clj}
                      {:source-paths ["cljx/test"]
                       :output-path "target/test"
                       :rules :clj}
                      {:source-paths ["cljx/src"]
                       :output-path "target/src"
                       :rules :cljs}
                      {:source-paths ["cljx/test"]
                       :output-path "target/test"
                       :rules :cljs}]}
  :cljsbuild {:builds [{:source-paths ["target/src"]
                        :compiler {:output-to "target/js/schedule.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]}
  :hooks [leiningen.cljsbuild cljx.hooks])
