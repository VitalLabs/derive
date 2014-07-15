(ns derive.repl
  (:require [clojure.browser.repl :as repl]
            [derive.tools :as tools]))

(defn connect []
  (tools/log "(repl/connect)")
  (repl/connect "http://localhost:9000/repl"))
