(defproject com.vitalreactor/derive "0.1.0-SNAPSHOT"
  :description "Simple library to support computing the 'latest' value derived from a mutable data source.  Designed to integrate into React/Om and with data stores like Datascript and NativeStore"
  :url "http://github.com/vitalreactor/derive"
  :license {:name "MIT License"
            :url "http://github.com/vitalreactor/derive/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2268"]
                 [im.chit/purnam.native "0.4.3"]
                 [prismatic/schema "0.2.2"]
                 [reagent "0.4.2"]
                 [figwheel "0.1.3-SNAPSHOT"]
                 [sablono "0.2.18"]
                 [vitalreactor/om "0.6.3.1"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-exec "0.3.3"]
            [lein-externs "0.1.3"]
            [clj-stacktrace "0.2.7"]
            [lein-figwheel "0.1.3-SNAPSHOT"]
            [com.cemerick/clojurescript.test "0.2.2"]
            [com.cemerick/austin "0.1.4"]]
  :profiles
  {:dev {:cljsbuild
         {:builds
          [ {:id "main"
             :source-paths ["src" "dev"]
             :compiler {:output-to "resources/public/js/derive.js"
                        :output-dir "resources/public/js/out"
                        :optimizations :none
                        :pretty-print true
                        :preamble ["templates/js/function_prototype_polyfill.js"]
                        :source-map true}}

            #_{:id "test"
             :source-paths ["src" "test"]
             :notify-command ["scripts/run_tests.rb" :cljs.test/runner]
             :compiler {:output-to "resources/public/js/derive.js"
                        ;:output-dir "resources/public/js/out"
                        :optimizations :none
                        :pretty-print true
                        :source-map true
                        :preamble ["templates/js/function_prototype_polyfill.js"]}} ]

;          :test-commands {"unit-tests" ["scripts/run_tests.rb" :runner]}
          :repl-launch-commands {"phantom" ["phantomjs" "phantom/repl.js"]
                                 "chrome" ["chrome"]}
          }

         :repl-options {:init (println "To start the browser-repl, run:\n"
                                       "(browser-repl)")
                        :port 4004
                        :caught clj-stacktrace.repl/pst+
                        :skip-default-init false}

         :injections [(require '[cljs.repl.browser :as brepl]
                               '[cemerick.piggieback :as pb])
                      (let [orig (ns-resolve (doto 'clojure.stacktrace require)
                                            'print-cause-trace)
                           new (ns-resolve (doto 'clj-stacktrace.repl require)
                                           'pst)]
                       (alter-var-root orig (constantly (deref new))))
                      (defn browser-repl []
                        (pb/cljs-repl :repl-env (brepl/repl-env :port 9000)))]

         :figwheel {:http-server-root "public" ;; assumes "resources"
                    :server-port 3450
                    :css-dirs ["resources/public/css/"]}
         }

   :release {}})
