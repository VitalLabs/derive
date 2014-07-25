(ns derive.repl
  (:require [clojure.browser.repl :as repl]))

(defn connect []
  (.log js/console "(repl/connect)")
  (repl/connect "http://localhost:9000/repl"))
